package com.astro.freecam.client.gui;

import com.astro.freecam.replay.ReplayData;
import com.astro.freecam.replay.ReplayFile;
import com.astro.freecam.replay.ReplayManager;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.StringTextComponent;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class ReplayStudioScreen extends Screen {
    private final Minecraft mc = Minecraft.getInstance();
    private List<Path> files;
    private int selected = -1;
    private String info = "Select a replay";
    private long infoUntil;

    public ReplayStudioScreen() { super(new StringTextComponent("Astro Replay Studio")); }

    @Override
    protected void init() {
        refresh();
        int cx = width / 2;
        int left = cx - 154;
        int right = cx + 5;

        addButton(new Button(left, height - 46, 74, 20, new StringTextComponent("Record"), b -> ReplayManager.startRecording("")));
        addButton(new Button(left + 78, height - 46, 74, 20, new StringTextComponent("Stop"), b -> ReplayManager.stopRecording()));
        addButton(new Button(right, height - 46, 74, 20, new StringTextComponent("Refresh"), b -> refresh()));
        addButton(new Button(right + 78, height - 46, 74, 20, new StringTextComponent("Close"), b -> onClose()));

        addButton(new Button(left, height - 22, 74, 20, new StringTextComponent("Load"), b -> loadSelected()));
        addButton(new Button(left + 78, height - 22, 74, 20, new StringTextComponent("Delete"), b -> deleteSelected()));
        addButton(new Button(right, height - 22, 74, 20, new StringTextComponent("Image"), b -> ReplayManager.captureThumbnail()));
        addButton(new Button(right + 78, height - 22, 74, 20, new StringTextComponent("Controls"), b -> { }));
    }

    private void refresh() {
        files = ReplayManager.listReplays();
        if (files.isEmpty()) selected = -1;
        else if (selected < 0 || selected >= files.size()) selected = 0;
        if (selected >= 0) loadInfo(files.get(selected));
    }

    private void loadInfo(Path file) {
        try {
            ReplayData data = ReplayFile.read(file);
            info = String.format(Locale.ROOT, "%s  •  %d frames  •  %s  •  %.1fs", file.getFileName(), data.size(), data.getDimension(), data.getDurationMillis()/1000.0D);
        } catch (Throwable t) { info = file.getFileName() + "  •  unreadable replay"; }
        infoUntil = System.currentTimeMillis() + 5000L;
    }

    private Path selectedFile() { return files != null && selected >= 0 && selected < files.size() ? files.get(selected) : null; }
    private void loadSelected() { Path p=selectedFile(); if(p==null){info="Select a replay first";infoUntil=System.currentTimeMillis()+3000L;return;} onClose(); ReplayManager.startPlayback(p); }
    private void deleteSelected() { Path p=selectedFile(); if(p==null){info="Select a replay first";infoUntil=System.currentTimeMillis()+3000L;return;} try{ReplayManager.delete(p);refresh();}catch(Throwable t){info="Delete failed: "+safe(t);infoUntil=System.currentTimeMillis()+4000L;} }

    @Override public void onClose() { mc.displayGuiScreen(null); }

    @Override
    public boolean mouseClicked(double mouseX,double mouseY,int button) {
        int top=50,row=22,cx=width/2;
        if(mouseX>=cx-154&&mouseX<=cx-8&&mouseY>=top&&mouseY<height-58){
            int idx=(int)((mouseY-top)/row);
            if(files!=null&&idx>=0&&idx<files.size()){selected=idx;loadInfo(files.get(idx));return true;}
        }
        return super.mouseClicked(mouseX,mouseY,button);
    }

    @Override
    public boolean keyPressed(int keyCode,int scanCode,int modifiers) {
        if(keyCode==256){onClose();return true;}
        return super.keyPressed(keyCode,scanCode,modifiers);
    }

    @Override
    public void render(MatrixStack m,int mouseX,int mouseY,float partialTicks) {
        renderBackground(m);
        int cx=width/2;
        fill(m,cx-165,4,cx+165,height-4,0xEE101419);
        fill(m,cx-158,32,cx+158,height-54,0xC4161B22);
        drawCenteredString(m,font,"§bASTRO REPLAY STUDIO",cx,10,0xFFFFFF);
        drawCenteredString(m,font,"Timeline • Keyframes • Player Eye • Freecam • Camera Tools",cx,22,0xA9B4BE);
        drawString(m,font,"REPLAYS",cx-152,38,0xFFFFFF);
        drawString(m,font,"DETAILS",cx+8,38,0xFFFFFF);

        if(files==null||files.isEmpty()) drawCenteredString(m,font,"No .replay files yet",cx-80,90,0x9098A0);
        else {
            int y=50;
            for(int i=0;i<files.size()&&y<height-62;i++,y+=22){
                boolean sel=i==selected;
                fill(m,cx-153,y-2,cx-8,y+18,sel?0xFF315C7C:0x77303942);
                String name=files.get(i).getFileName().toString(); if(name.length()>22)name=name.substring(0,19)+"...";
                drawString(m,font,name,cx-147,y+3,0xE8EEF2);
            }
        }

        Path p=selectedFile();
        if(p!=null){
            drawString(m,font,p.getFileName().toString(),cx+8,52,0xD8E5EE);
            drawString(m,font,info,cx+8,68,0xA8B1B8);
            drawString(m,font,"TOOLS",cx+8,92,0xFFFFFF);
            drawString(m,font,"P Pause   O POV   M Marker   K Keyframe",cx+8,110,0xC2CAD0);
            drawString(m,font,"T Image   H HUD   ←/→ Seek   ↑/↓ Speed",cx+8,126,0xC2CAD0);
            drawString(m,font,"F4 Freecam   F9 Record   F10 Studio",cx+8,142,0xC2CAD0);
            drawString(m,font,"Cinematic view uses K keyframes.",cx+8,168,0x90B7CE);
            drawString(m,font,"Recording: " + (ReplayManager.isRecording()?"ACTIVE":"ready"),cx+8,190,ReplayManager.isRecording()?0xFF7777:0x8FA1AC);
            drawString(m,font,"Images: .minecraft/freecam/replays/images",cx+8,208,0x76828B);
        }

        if(infoUntil>System.currentTimeMillis()) drawCenteredString(m,font,info,cx,height-63,0xD0D8DD);
        super.render(m,mouseX,mouseY,partialTicks);
    }

    private static String safe(Throwable t){return t==null||t.getMessage()==null?"unknown error":t.getMessage();}
}
