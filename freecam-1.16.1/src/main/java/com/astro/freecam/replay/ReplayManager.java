package com.astro.freecam.replay;

import com.astro.freecam.client.FreecamCamera;
import com.astro.freecam.client.FreecamClient;
import com.astro.freecam.client.ReplayCamera;
import com.astro.freecam.client.ReplayGhostPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.StringTextComponent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ReplayManager {
    public enum ViewMode { PLAYER_EYE, RECORDED_CAMERA, CINEMATIC, THIRD_PERSON, FRONT_THIRD_PERSON }

    private static final Minecraft MC = Minecraft.getInstance();
    private static final Path DIR = Paths.get("freecam", "replays");
    private static final ReplayRecorder RECORDER = new ReplayRecorder(MC);
    private static final ReplayMetadata EMPTY_METADATA = new ReplayMetadata();

    private static ReplayData playback;
    private static Path playbackFile;
    private static ReplayMetadata metadata = EMPTY_METADATA;
    private static ReplayCamera playbackCamera;
    private static ReplayGhostPlayer ghost;
    private static int playbackIndex;
    private static boolean paused;
    private static double speed = 1.0D;
    private static double accumulator;
    private static ViewMode viewMode = ViewMode.PLAYER_EYE;

    private ReplayManager() {}

    public static boolean isRecording() { return RECORDER.isRecording(); }
    public static boolean isPlaying() { return playback != null; }
    public static boolean isPaused() { return paused; }
    public static boolean isFreecamView() { return viewMode == ViewMode.RECORDED_CAMERA || viewMode == ViewMode.CINEMATIC; }
    public static String viewName() { return viewMode.name().replace('_', ' '); }
    public static double getSpeed() { return speed; }
    public static int getFrameIndex() { return playbackIndex; }
    public static int getFrameCount() { return playback == null ? 0 : playback.size(); }
    public static double getDurationSeconds() { return playback == null ? 0D : playback.getDurationMillis() / 1000.0D; }
    public static List<ReplayMarker> markers() { return metadata.markers(); }
    public static List<ReplayKeyframe> keyframes() { return metadata.keyframes(); }

    public static Path replayDir() { return MC.gameDir.toPath().resolve(DIR); }

    public static List<Path> listReplays() {
        Path dir = replayDir();
        try {
            Files.createDirectories(dir);
            List<Path> files = new ArrayList<Path>();
            java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.replay");
            try { for (Path path : stream) if (Files.isRegularFile(path)) files.add(path); }
            finally { stream.close(); }
            Collections.sort(files, new Comparator<Path>() {
                @Override public int compare(Path a, Path b) {
                    try { return Long.compare(Files.getLastModifiedTime(b).toMillis(), Files.getLastModifiedTime(a).toMillis()); }
                    catch (IOException e) { return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()); }
                }
            });
            return files;
        } catch (IOException e) { return Collections.emptyList(); }
    }

    public static Path createRecordingPath(String requestedName) throws IOException {
        Path dir=replayDir().toAbsolutePath().normalize(); Files.createDirectories(dir);
        String base=sanitize(requestedName);
        if(base.isEmpty())base="replay_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT).format(new Date());
        if(!base.toLowerCase(Locale.ROOT).endsWith(".replay"))base+=".replay";
        Path file=dir.resolve(base).normalize();
        if(!dir.equals(file.getParent()))throw new IOException("Invalid replay name");
        if(Files.exists(file)){
            String stem=base.substring(0,base.length()-7); int n=2;
            do file=dir.resolve(stem+"_"+n+++".replay"); while(Files.exists(file));
        }
        return file;
    }

    public static void startRecording(String requestedName) {
        if(MC.player==null||MC.world==null||RECORDER.isRecording()||playback!=null)return;
        try{Path file=createRecordingPath(requestedName);RECORDER.start(file);notify("§c● RECORDING  "+file.getFileName());}
        catch(Throwable t){notifyError("Could not start recording: "+safe(t));}
    }

    public static void captureTick() {
        if(!RECORDER.isRecording()||MC.player==null||MC.world==null)return;
        Entity view=MC.getRenderViewEntity();
        boolean freecam=FreecamClient.enabled()&&view instanceof FreecamCamera;
        double x=freecam?view.getPosX():MC.player.getPosX(), y=freecam?view.getPosY():MC.player.getPosYEye(), z=freecam?view.getPosZ():MC.player.getPosZ();
        float yaw=freecam?view.rotationYaw:MC.player.rotationYaw, pitch=freecam?view.rotationPitch:MC.player.rotationPitch;
        RECORDER.capture(freecam,x,y,z,yaw,pitch);
    }

    public static void stopRecording() {
        if(!RECORDER.isRecording())return;
        Path file=RECORDER.stop();
        if(RECORDER.getFailure()==null)notify("§aReplay saved: "+(file==null?"replay.replay":file.getFileName()));
        else notifyError("Replay recording error: "+safe(RECORDER.getFailure()));
    }

    public static void startPlayback(Path file) {
        if(file==null||MC.world==null)return;
        try{
            Path normalized=file.toAbsolutePath().normalize(),dir=replayDir().toAbsolutePath().normalize();
            if(!Files.isRegularFile(normalized)||!dir.equals(normalized.getParent()))throw new IOException("Invalid replay file");
            ReplayData data=ReplayFile.read(normalized);
            if(RECORDER.isRecording())stopRecording();
            FreecamClient.stopForReplay(); stopPlayback();
            playback=data; playbackFile=normalized; metadata=ReplayMetadata.load(normalized);
            playbackIndex=0; accumulator=0D; paused=false; speed=1D; viewMode=ViewMode.PLAYER_EYE;
            playbackCamera=new ReplayCamera(MC.world,MC.player==null?null:MC.player.getGameProfile());
            ghost=new ReplayGhostPlayer(MC.world,MC.player==null?null:MC.player.getGameProfile());
            MC.world.addPlayer(ReplayGhostPlayer.ENTITY_ID,ghost);
            applyFrame(); notify("§aLoaded: "+normalized.getFileName());
        }catch(Throwable t){notifyError("Could not load replay: "+safe(t));}
    }

    public static void tickPlayback() {
        if(playback==null||MC.world==null||playbackCamera==null||ghost==null)return;
        if(!paused&&playback.size()>1){
            accumulator+=speed;
            while(accumulator>=1D&&playbackIndex<playback.size()-1){playbackIndex++;accumulator-=1D;}
            if(playbackIndex>=playback.size()-1){playbackIndex=playback.size()-1;accumulator=0D;paused=true;}
        }
        applyFrame();
    }

    public static void togglePause(){if(playback!=null)paused=!paused;}
    public static void cycleView(){if(playback==null)return;ViewMode[]m=ViewMode.values();viewMode=m[(viewMode.ordinal()+1)%m.length];applyFrame();notify("View: "+viewName());}
    public static void setView(ViewMode m){if(playback!=null&&m!=null){viewMode=m;applyFrame();}}

    public static void seek(int frames){
        if(playback==null||playback.size()==0)return;
        playbackIndex=MathHelper.clamp(playbackIndex+frames,0,playback.size()-1);accumulator=0D;paused=false;applyFrame();
    }
    public static void seekToPercent(double percent){
        if(playback==null||playback.size()==0)return;
        percent=Math.max(0D,Math.min(1D,percent));playbackIndex=MathHelper.clamp((int)Math.round((playback.size()-1)*percent),0,playback.size()-1);accumulator=0D;applyFrame();
    }
    public static void setSpeed(double value){speed=MathHelper.clamp(value,.25D,4D);notify(String.format(Locale.ROOT,"Speed: %.2fx",speed));}

    public static void addMarker(){
        if(playback==null)return;
        metadata.addMarker(new ReplayMarker(playbackIndex,"Marker "+(metadata.markers().size()+1)));saveMetadataQuietly();
        notify("Marker added at "+frameTime());
    }

    public static void addKeyframe(){
        if(playback==null||playback.size()==0)return;
        Entity e=MC.getRenderViewEntity();if(e==null)e=MC.player;if(e==null)return;
        metadata.addKeyframe(new ReplayKeyframe(playbackIndex,e.getPosX(),e.getPosY(),e.getPosZ(),e.rotationYaw,e.rotationPitch));saveMetadataQuietly();
        notify("Camera keyframe added at "+frameTime());
    }

    public static void captureThumbnail(){
        if(MC.world==null)return;
        try{
            Path dir=replayDir().resolve("images");Files.createDirectories(dir);
            String name=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.ROOT).format(new Date())+".png";
            File file=dir.resolve(name).toFile();
            ScreenShotHelper.grab(file,MC.getMainWindow().getWidth(),MC.getMainWindow().getHeight(),MC.getFramebuffer(),message->notify("§aImage saved: "+file.getName()));
        }catch(Throwable t){notifyError("Image capture failed: "+safe(t));}
    }

    public static void delete(Path file)throws IOException{
        if(file==null)return;
        Path normalized=file.toAbsolutePath().normalize(),dir=replayDir().toAbsolutePath().normalize();
        if(!dir.equals(normalized.getParent()))throw new IOException("Invalid replay path");
        if(playbackFile!=null&&playbackFile.equals(normalized))stopPlayback();
        Files.deleteIfExists(normalized);Files.deleteIfExists(ReplayMetadata.metaPath(normalized));
    }

    public static void stopPlayback(){
        try{if(MC.player!=null)MC.setRenderViewEntity(MC.player);}catch(Throwable ignored){}
        if(MC.world!=null&&ghost!=null)try{ghost.remove();}catch(Throwable ignored){}
        if(playbackCamera!=null)try{playbackCamera.remove();}catch(Throwable ignored){}
        playback=null;playbackFile=null;metadata=EMPTY_METADATA;playbackIndex=0;paused=false;accumulator=0D;speed=1D;viewMode=ViewMode.PLAYER_EYE;playbackCamera=null;ghost=null;
    }

    public static void lifecycleReset(){if(RECORDER.isRecording())stopRecording();stopPlayback();}

    private static void applyFrame(){
        if(playback==null||playback.size()==0||MC.world==null||playbackCamera==null||ghost==null)return;
        ReplayFrame a=playback.getFrames().get(playbackIndex),b=playbackIndex<playback.size()-1?playback.getFrames().get(playbackIndex+1):a;
        double t=paused?0D:accumulator;
        double px=lerp(a.playerX,b.playerX,t),py=lerp(a.playerY,b.playerY,t),pz=lerp(a.playerZ,b.playerZ,t);
        float pyaw=angleLerp(a.playerYaw,b.playerYaw,(float)t),ppitch=(float)lerp(a.playerPitch,b.playerPitch,t);
        setEntity(ghost,px,py,pz,pyaw,ppitch);

        ReplayCameraPose pose;
        if(viewMode==ViewMode.PLAYER_EYE)pose=new ReplayCameraPose(px,py,pz,pyaw,ppitch);
        else if(viewMode==ViewMode.RECORDED_CAMERA)pose=new ReplayCameraPose(lerp(a.cameraX,b.cameraX,t),lerp(a.cameraY,b.cameraY,t),lerp(a.cameraZ,b.cameraZ,t),angleLerp(a.cameraYaw,b.cameraYaw,(float)t),(float)lerp(a.cameraPitch,b.cameraPitch,t));
        else if(viewMode==ViewMode.CINEMATIC&&!metadata.keyframes().isEmpty())pose=cinematicPose(playbackIndex+t);
        else if(viewMode==ViewMode.FRONT_THIRD_PERSON||viewMode==ViewMode.THIRD_PERSON)pose=thirdPersonPose(px,py,pz,pyaw,viewMode==ViewMode.FRONT_THIRD_PERSON);
        else pose=new ReplayCameraPose(px,py,pz,pyaw,ppitch);

        playbackCamera.setPositionAndRotation(pose.x,pose.y,pose.z,pose.yaw,pose.pitch);
        playbackCamera.prevPosX=pose.x;playbackCamera.prevPosY=pose.y;playbackCamera.prevPosZ=pose.z;playbackCamera.prevRotationYaw=pose.yaw;playbackCamera.prevRotationPitch=pose.pitch;
        if(MC.getRenderViewEntity()!=playbackCamera)MC.setRenderViewEntity(playbackCamera);
    }

    private static ReplayCameraPose cinematicPose(double frame){
        List<ReplayKeyframe>keys=metadata.keyframes();if(keys.size()==1){ReplayKeyframe k=keys.get(0);return new ReplayCameraPose(k.getX(),k.getY(),k.getZ(),k.getYaw(),k.getPitch());}
        ReplayKeyframe before=keys.get(0),after=keys.get(keys.size()-1);
        for(ReplayKeyframe k:keys){if(k.getFrame()<=frame)before=k;if(k.getFrame()>=frame){after=k;break;}}
        if(before==after)return new ReplayCameraPose(before.getX(),before.getY(),before.getZ(),before.getYaw(),before.getPitch());
        double t=(frame-before.getFrame())/Math.max(1D,after.getFrame()-before.getFrame());t=Math.max(0D,Math.min(1D,t));
        return new ReplayCameraPose(lerp(before.getX(),after.getX(),t),lerp(before.getY(),after.getY(),t),lerp(before.getZ(),after.getZ(),t),angleLerp(before.getYaw(),after.getYaw(),(float)t),(float)lerp(before.getPitch(),after.getPitch(),t));
    }

    private static ReplayCameraPose thirdPersonPose(double x,double y,double z,float yaw,boolean front){
        double r=Math.toRadians(yaw),fx=-Math.sin(r),fz=Math.cos(r),sign=front?1D:-1D,d=4D;
        float cyaw=front?MathHelper.wrapDegrees(yaw+180F):yaw;
        return new ReplayCameraPose(x+fx*d*sign,y+1.25D,z+fz*d*sign,cyaw,0F);
    }

    private static void setEntity(ReplayGhostPlayer entity,double x,double y,double z,float yaw,float pitch){
        entity.setPositionAndRotation(x,y,z,yaw,pitch);entity.rotationYawHead=yaw;entity.renderYawOffset=yaw;
        entity.prevRotationYaw=yaw;entity.prevRotationPitch=pitch;entity.prevRotationYawHead=yaw;entity.prevRenderYawOffset=yaw;
        entity.lastTickPosX=x;entity.lastTickPosY=y;entity.lastTickPosZ=z;
    }

    private static void saveMetadataQuietly(){if(playbackFile==null)return;try{metadata.save(playbackFile);}catch(Throwable t){notifyError("Metadata save failed: "+safe(t));}}
    private static double lerp(double a,double b,double t){return a+(b-a)*t;}
    private static float angleLerp(float a,float b,float t){return a+MathHelper.wrapDegrees(b-a)*t;}
    private static String frameTime(){int s=(int)(playbackIndex/20D);return String.format(Locale.ROOT,"%02d:%02d",s/60,s%60);}
    private static String sanitize(String raw){if(raw==null)return"";String v=raw.trim().replaceAll("[\\\\/:*?\"<>|]","_");while(v.endsWith("."))v=v.substring(0,v.length()-1);return v;}
    private static String safe(Throwable t){return t==null||t.getMessage()==null?"unknown error":t.getMessage();}
    private static void notify(String message){try{if(MC.player!=null)MC.player.sendStatusMessage(new StringTextComponent("§bReplay: "+message),true);}catch(Throwable ignored){}}
    private static void notifyError(String message){try{if(MC.player!=null)MC.player.sendStatusMessage(new StringTextComponent("§cReplay: "+message),false);}catch(Throwable ignored){}}

    private static final class ReplayCameraPose{
        final double x,y,z;final float yaw,pitch;
        ReplayCameraPose(double x,double y,double z,float yaw,float pitch){this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.pitch=pitch;}
    }
}
