from pathlib import Path
import sys

target = Path(sys.argv[1])
source = target.read_text(encoding="utf-8")

replacements = [
    (
        "import java.util.List;\n",
        "import java.util.List;\nimport java.util.EnumSet;\n",
        "java imports",
    ),
    (
        "import net.minecraft.entity.ai.goal.FollowOwnerGoal;\n",
        "import net.minecraft.entity.ai.goal.FollowOwnerGoal;\nimport net.minecraft.entity.ai.goal.Goal;\n",
        "Goal import",
    ),
    (
        "import net.minecraft.entity.ai.goal.SitGoal;\n",
        "",
        "obsolete SitGoal import",
    ),
    (
        "this.goalSelector.addGoal(2, new SitGoal(this));",
        "this.goalSelector.addGoal(2, new CommandedSitGoal(this));",
        "custom sit goal registration",
    ),
    (
        "    protected void readAdditional(CompoundNBT compound) {\n"
        "        super.readAdditional(compound);\n"
        "        this.commandedToSit = compound.getBoolean(\"AstroCommandedToSit\");\n"
        "        this.setSitting(this.commandedToSit);\n"
        "    }",
        "    public void readAdditional(CompoundNBT compound) {\n"
        "        super.readAdditional(compound);\n"
        "        this.commandedToSit = compound.getBoolean(\"AstroCommandedToSit\");\n"
        "    }",
        "readAdditional visibility and legacy call",
    ),
    (
        "    protected void writeAdditional(CompoundNBT compound) {",
        "    public void writeAdditional(CompoundNBT compound) {",
        "writeAdditional visibility",
    ),
    (
        "        this.commandedToSit = sitting;\n"
        "        this.setSitting(sitting);\n"
        "        if (sitting) {",
        "        this.commandedToSit = sitting;\n"
        "        if (sitting) {",
        "remove unavailable sitting API",
    ),
]

for old, new, label in replacements:
    if old not in source:
        raise SystemExit(f"Expected source fragment missing: {label}")
    source = source.replace(old, new, 1)

goal_class = '''
    /** Stops movement while the owner has commanded the companion to sit. */
    private static final class CommandedSitGoal extends Goal {
        private final PokieSnowmanEntity snowman;

        private CommandedSitGoal(PokieSnowmanEntity snowman) {
            this.snowman = snowman;
            this.setMutexFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
        }

        @Override
        public boolean shouldExecute() {
            return this.snowman.commandedToSit;
        }

        @Override
        public boolean shouldContinueExecuting() {
            return this.snowman.commandedToSit;
        }

        @Override
        public void startExecuting() {
            this.snowman.getNavigator().clearPath();
        }

        @Override
        public void tick() {
            this.snowman.getNavigator().clearPath();
        }
    }

'''
anchor = "    private void makeTemporarySnowCage"
if anchor not in source:
    raise SystemExit("Expected makeTemporarySnowCage method anchor missing")
source = source.replace(anchor, goal_class + anchor, 1)

if "setSitting(" in source or "new SitGoal" in source:
    raise SystemExit("Legacy sitting calls remain after patch")

target.write_text(source, encoding="utf-8")
print(f"Patched {target}")
