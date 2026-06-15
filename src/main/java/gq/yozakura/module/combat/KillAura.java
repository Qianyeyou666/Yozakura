package gq.yozakura.module.combat;

import com.google.common.base.CaseFormat;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;
import gq.yozakura.module.ModuleType;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;
import gq.yozakura.runtime.YozakuraRuntime;
import gq.yozakura.manager.BlinkModules;
import gq.yozakura.event.bus.EventManager;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bus.types.Priority;
import gq.yozakura.event.bridge.*;
import gq.yozakura.manager.RotationState;
import gq.yozakura.manager.RotationDebug;
import gq.yozakura.manager.VisualRotationState;
import gq.yozakura.bridge.MinecraftAccessor;
import gq.yozakura.module.runtime.Module;
import gq.yozakura.module.world.BedNuker;
import gq.yozakura.module.player.AutoBlockIn;
import gq.yozakura.module.player.AutoHeal;
import gq.yozakura.module.world.Scaffold;
import gq.yozakura.module.render.runtime.HUD;
import gq.yozakura.value.properties.*;
import gq.yozakura.util.module.*;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Random;

public class KillAura extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode;
    public final ModeProperty sort;
    public ModeProperty autoBlock;
    private final BooleanProperty noSwap = new BooleanProperty("NoSwap",true,() -> this.autoBlock.getValue() == 2);
    private final IntProperty maxTick = new IntProperty("MaxTick",3,1,5,() -> this.autoBlock.getValue() == 6);
    private final IntProperty startBlinkTick = new IntProperty("StartBlinkTick",0,1,5,() -> this.autoBlock.getValue() == 6);
    private final IntProperty stopBlinkTick = new IntProperty("StopBlinkTick",2,1,5,() -> this.autoBlock.getValue() == 6);
    private final IntProperty swapTick = new IntProperty("SwapTick",2,1,5,() -> this.autoBlock.getValue() == 6);
    private final IntProperty switchBackTick = new IntProperty("SwitchBackTick",2,1,5,() -> this.autoBlock.getValue() == 6);
    private final IntProperty stopBlockTick = new IntProperty("StopBlockTick",2,1,5,() -> this.autoBlock.getValue() == 6);
    public final IntProperty attackTick = new IntProperty("AttackTick",0,1,5,() -> this.autoBlock.getValue() == 6);
    private final IntProperty startBlockTick = new IntProperty("StartBlockTick",0,1,5,() -> this.autoBlock.getValue() == 6);
    private final BooleanProperty postStartBlock = new BooleanProperty("PostBlock",false,() -> this.autoBlock.getValue() == 6);
    public final BooleanProperty autoBlockRequirePress;
    public final IntProperty autoBlockCPS;
    public final FloatProperty autoBlockRange;

    public final FloatProperty swingRange;
    public final FloatProperty attackRange;
    public final IntProperty fov;
    public final IntProperty minCPS;
    public final IntProperty maxCPS;
    public final IntProperty switchDelay;
    public final ModeProperty rotations;
    public final ModeProperty moveFix;
    public final PercentProperty smoothing;
    public final IntProperty angleStep;
    public final BooleanProperty throughWalls;
    public final BooleanProperty requirePress;
    public final BooleanProperty allowMining;
    public final BooleanProperty weaponsOnly;
    public final BooleanProperty allowTools;
    public final BooleanProperty inventoryCheck;
    public final BooleanProperty botCheck;
    public final BooleanProperty players;
    public final BooleanProperty bosses;
    public final BooleanProperty mobs;
    public final BooleanProperty animals;
    public final BooleanProperty golems;
    public final BooleanProperty silverfish;
    public final BooleanProperty teams;
    public final ModeProperty showTarget;
    public final BooleanProperty rotationDebug;

    private final TimerUtil timer = new TimerUtil();
    public static EntityLivingBase target;
    private AttackData attackTarget = null;
    private int switchTick = 0;
    private boolean hitRegistered = false;
    private boolean blockingState = false;
    private boolean isBlocking = false;
    private boolean fakeBlockState = false;
    private long attackDelayMS = 0L;
    public int blockTick = 0;
    private boolean swapped = false;
    private boolean postBlock = false;
    private boolean postSwap = false;


    public KillAura() {
        super("KillAura", false);
        this.key = Keyboard.KEY_R;
        this.category = ModuleType.Combat;
        this.Chinese = "杀戮光环";
        this.Descript = "Attack targets with server-side rotations and autoblock";
        this.About = this.Descript;
        this.mode = new ModeProperty("Mode", 0, new String[]{"Single", "Switch"});
        this.sort = new ModeProperty("Sort", 0, new String[]{"Distance", "Health", "Hurt Time", "FOV"});

        this.autoBlock = new ModeProperty(
                "AutoBlock", 0, new String[]{"None", "Vanilla", "Hypixel", "Legit", "Fake","Hypixel Test","Hypixel Custom"}
        );
        this.autoBlockRequirePress = new BooleanProperty("AutoBlock Require Press", false);
        this.autoBlockCPS = new IntProperty("AutoBlock Aps", 10, 1, 20);
        this.autoBlockRange = new FloatProperty("AutoBlock Range", 6.0F, 3.0F, 8.0F);
        this.swingRange = new FloatProperty("Swing Range", 3.5F, 3.0F, 6.0F);
        this.attackRange = new FloatProperty("Attack Range", 3.0F, 3.0F, 6.0F);
        this.fov = new IntProperty("Fov", 360, 30, 360);
        this.minCPS = new IntProperty("Min Aps", 14, 1, 20);
        this.maxCPS = new IntProperty("Max Aps", 14, 1, 20);
        this.switchDelay = new IntProperty("Switch Delay", 150, 0, 1000);
        this.rotations = new ModeProperty("Rotations", 2, new String[]{"None", "Legit", "Silent", "Lock View"});
        this.moveFix = new ModeProperty("Move Fix", 1, new String[]{"None", "Silent", "Strict"});
        this.smoothing = new PercentProperty("Smoothing", 0);
        this.angleStep = new IntProperty("Angle Step", 90, 30, 180);
        this.throughWalls = new BooleanProperty("Through Walls", true);
        this.requirePress = new BooleanProperty("Require Press", false);
        this.allowMining = new BooleanProperty("Allow Mining", false);
        this.weaponsOnly = new BooleanProperty("Weapons Only", false);
        this.allowTools = new BooleanProperty("Allow Tools", false, this.weaponsOnly::getValue);
        this.inventoryCheck = new BooleanProperty("Inventory Check", true);
        this.botCheck = new BooleanProperty("Bot Check", true);
        this.players = new BooleanProperty("Players", true);
        this.bosses = new BooleanProperty("Bosses", false);
        this.mobs = new BooleanProperty("Mobs", false);
        this.animals = new BooleanProperty("Animals", false);
        this.golems = new BooleanProperty("Golems", false);
        this.silverfish = new BooleanProperty("Silverfish", false);
        this.teams = new BooleanProperty("Teams", true);
        this.showTarget = new ModeProperty("Show Target", 0, new String[]{"None", "Default", "Hud", "Scan"});
        this.rotationDebug = new BooleanProperty("Rotation Debug", false);
    }

    private long getAttackDelay() {
        return this.isBlocking ? (long) (1000.0F / this.autoBlockCPS.getValue()) : 1000L / RandomUtil.nextLong(this.minCPS.getValue(), this.maxCPS.getValue());
    }

    private boolean performAttack(float yaw, float pitch) {
        if (!YozakuraRuntime.playerStateManager.digging && !YozakuraRuntime.playerStateManager.placing) {
            if (this.isPlayerBlocking() && this.autoBlock.getValue() != 1) {
                return false;
            } else if (this.attackDelayMS > 0L) {
                return false;
            } else {
                this.attackDelayMS = this.attackDelayMS + this.getAttackDelay();
                mc.thePlayer.swingItem();
                if ((this.rotations.getValue() != 0 || !this.isBoxInAttackRange(this.attackTarget.getBox()))
                        && RotationUtil.rayTrace(this.attackTarget.getBox(), yaw, pitch, this.attackRange.getValue()) == null) {
                    return false;
                } else {
                    AttackEvent event = new AttackEvent(this.attackTarget.getEntity());
                    EventManager.call(event);
                    MinecraftAccessor.syncCurrentPlayItem(mc.playerController);
                    PacketUtil.sendPacket(new C02PacketUseEntity(this.attackTarget.getEntity(), Action.ATTACK));
                    if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR) {
                        PlayerUtil.attackEntity(this.attackTarget.getEntity());
                    }
                    this.hitRegistered = true;
                    return true;
                }
            }
        } else {
            return false;
        }
    }

    private void sendUseItem() {
        MinecraftAccessor.syncCurrentPlayItem(mc.playerController);
        this.startBlock(mc.thePlayer.getHeldItem());
    }
    private void startBlock(ItemStack itemStack) {
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(itemStack));
        mc.thePlayer.setItemInUse(itemStack, itemStack.getMaxItemUseDuration());
        this.blockingState = true;
    }

    private void stopBlock() {
        PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        mc.thePlayer.stopUsingItem();
        this.blockingState = false;
    }

    private void interactAttack(float yaw, float pitch) {
        if (this.attackTarget != null) {
            MovingObjectPosition mop = RotationUtil.rayTrace(this.attackTarget.getBox(), yaw, pitch, 8.0);
            if (mop != null) {
                MinecraftAccessor.syncCurrentPlayItem(mc.playerController);
                PacketUtil.sendPacket(
                        new C02PacketUseEntity(
                                this.attackTarget.getEntity(),
                                new Vec3(mop.hitVec.xCoord - this.attackTarget.getX(), mop.hitVec.yCoord - this.attackTarget.getY(), mop.hitVec.zCoord - this.attackTarget.getZ())
                        )
                );
                PacketUtil.sendPacket(new C02PacketUseEntity(this.attackTarget.getEntity(), Action.INTERACT));
                PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
                this.blockingState = true;
            }
        }
    }

    private boolean canAttack() {
        if (this.inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer) {
            return false;
        } else if (!(Boolean) this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
            if (MinecraftAccessor.isHittingBlock(mc.playerController)) {
                return false;
            } else if ((ItemUtil.isEating() || ItemUtil.isUsingBow()) && PlayerUtil.isUsingItem()) {
                return false;
            } else {
                AutoHeal autoHeal = (AutoHeal) YozakuraRuntime.moduleManager.modules.get(AutoHeal.class);
                if (autoHeal.isEnabled() && autoHeal.isSwitching()) {
                    return false;
                } else {
                    BedNuker bedNuker = (BedNuker) YozakuraRuntime.moduleManager.modules.get(BedNuker.class);
                    AutoBlockIn autoBlockIn = (AutoBlockIn) YozakuraRuntime.moduleManager.modules.get(AutoBlockIn.class);
                    if (bedNuker.isEnabled() && bedNuker.isReady()) {
                        return false;
                    } else if (YozakuraRuntime.moduleManager.modules.get(Scaffold.class).isEnabled()) {
                        return false;
                    } else if (autoBlockIn.isEnabled()) {
                        return false;
                    } else if (this.requirePress.getValue()) {
                        return PlayerUtil.isAttacking();
                    } else {
                        MovingObjectPosition objectMouseOver = mc.objectMouseOver;
                        return !this.allowMining.getValue()
                                || objectMouseOver == null
                                || objectMouseOver.typeOfHit != MovingObjectType.BLOCK
                                || !PlayerUtil.isAttacking();
                    }
                }
            }
        } else {
            return false;
        }
    }

    private boolean canAutoBlock() {
        if (!ItemUtil.isHoldingSword()) {
            return false;
        } else {
            return !this.autoBlockRequirePress.getValue() || PlayerUtil.isUsingItem();
        }
    }

    private boolean hasValidTarget() {
        return mc.theWorld
                .loadedEntityList
                .stream()
                .anyMatch(
                        entity -> entity instanceof EntityLivingBase
                                && this.isValidTarget((EntityLivingBase) entity)
                                && this.isInBlockRange((EntityLivingBase) entity)
                );
    }

    private boolean isValidTarget(EntityLivingBase entityLivingBase) {
        if (!mc.theWorld.loadedEntityList.contains(entityLivingBase)) {
            return false;
        } else if (entityLivingBase != mc.thePlayer && entityLivingBase != mc.thePlayer.ridingEntity) {
            if (entityLivingBase == mc.getRenderViewEntity() || entityLivingBase == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityLivingBase.deathTime > 0) {
                return false;
            } else if (RotationUtil.angleToEntity(entityLivingBase) > this.fov.getValue().floatValue()) {
                return false;
            } else if (!this.throughWalls.getValue() && !RotationUtil.hasVisiblePoint(entityLivingBase.getEntityBoundingBox().expand(entityLivingBase.getCollisionBorderSize(), entityLivingBase.getCollisionBorderSize(), entityLivingBase.getCollisionBorderSize()))) {
                return false;
            } else if (entityLivingBase instanceof EntityOtherPlayerMP) {
                if (!this.players.getValue()) {
                    return false;
                } else if (TeamUtil.isFriend((EntityPlayer) entityLivingBase)) {
                    return false;
                } else {
                    return (!this.teams.getValue() || !TeamUtil.isSameTeam((EntityPlayer) entityLivingBase)) && (!this.botCheck.getValue() || !TeamUtil.isBot((EntityPlayer) entityLivingBase));
                }
            } else if (entityLivingBase instanceof EntityDragon || entityLivingBase instanceof EntityWither) {
                return this.bosses.getValue();
            } else if (!(entityLivingBase instanceof EntityMob) && !(entityLivingBase instanceof EntitySlime)) {
                if (entityLivingBase instanceof EntityAnimal
                        || entityLivingBase instanceof EntityBat
                        || entityLivingBase instanceof EntitySquid
                        || entityLivingBase instanceof EntityVillager) {
                    return this.animals.getValue();
                } else if (!(entityLivingBase instanceof EntityIronGolem)) {
                    return false;
                } else {
                    return this.golems.getValue() && (!this.teams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase));
                }
            } else if (!(entityLivingBase instanceof EntitySilverfish)) {
                return this.mobs.getValue();
            } else {
                return this.silverfish.getValue() && (!this.teams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase));
            }
        } else {
            return false;
        }
    }

    private boolean isInRange(EntityLivingBase entityLivingBase) {
        return this.isInBlockRange(entityLivingBase) || this.isInSwingRange(entityLivingBase) || this.isInAttackRange(entityLivingBase);
    }

    private boolean isInBlockRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.autoBlockRange.getValue();
    }

    private boolean isInSwingRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.swingRange.getValue();
    }

    private boolean isBoxInSwingRange(AxisAlignedBB axisAlignedBB) {
        return RotationUtil.distanceToBox(axisAlignedBB) <= (double) this.swingRange.getValue();
    }

    private boolean isInAttackRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.attackRange.getValue();
    }

    private boolean isBoxInAttackRange(AxisAlignedBB axisAlignedBB) {
        return RotationUtil.distanceToBox(axisAlignedBB) <= (double) this.attackRange.getValue();
    }

    private boolean isPlayerTarget(EntityLivingBase entityLivingBase) {
        return entityLivingBase instanceof EntityPlayer && TeamUtil.isTarget((EntityPlayer) entityLivingBase);
    }



    public EntityLivingBase getTarget() {
        return this.attackTarget != null ? this.attackTarget.getEntity() : null;
    }

    public boolean isAttackAllowed() {
        Scaffold scaffold = (Scaffold) YozakuraRuntime.moduleManager.modules.get(Scaffold.class);
        if (scaffold.isEnabled()) {
            return false;
        } else if (!this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
            return !this.requirePress.getValue() || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
        } else {
            return false;
        }
    }

    public boolean shouldAutoBlock() {
        if (this.isPlayerBlocking() && this.isBlocking) {
            return !mc.thePlayer.isInWater() && !mc.thePlayer.isInLava() && (this.autoBlock.getValue() == 2 || this.autoBlock.getValue() == 3 || this.autoBlock.getValue() == 5 || this.autoBlock.getValue() == 6 || this.autoBlock.getValue() == 7);
        } else {
            return false;
        }
    }

    public boolean isBlocking() {
        return this.fakeBlockState && ItemUtil.isHoldingSword();
    }

    public boolean isPlayerBlocking() {
        return (mc.thePlayer.isUsingItem() || this.blockingState) && ItemUtil.isHoldingSword();
    }

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            RotationDebug.setSourceEnabled("KillAura", this.rotationDebug.getValue());
            if (this.attackDelayMS > 0L) {
                this.attackDelayMS -= 50L;
            }
            boolean attack = this.attackTarget != null && this.canAttack();
            boolean block = attack && this.canAutoBlock();
            if (!block) {
                YozakuraRuntime.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                this.isBlocking = false;
                this.fakeBlockState = false;
                this.blockTick = 0;
            }
            if (attack) {
                boolean swap = false;
                boolean blocked = false;
                if (block) {
                    switch (this.autoBlock.getValue()) {
                        case 0:
                            if (PlayerUtil.isUsingItem()) {
                                this.isBlocking = true;
                                if (!this.isPlayerBlocking() && !YozakuraRuntime.playerStateManager.digging && !YozakuraRuntime.playerStateManager.placing) {
                                    swap = true;
                                }
                            } else {
                                this.isBlocking = false;
                                if (this.isPlayerBlocking() && !YozakuraRuntime.playerStateManager.digging && !YozakuraRuntime.playerStateManager.placing) {
                                    this.stopBlock();
                                }
                            }
                            YozakuraRuntime.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                            this.fakeBlockState = false;
                            break;
                        case 1:
                            if (this.hasValidTarget()) {
                                if (!this.isPlayerBlocking() && !YozakuraRuntime.playerStateManager.digging && !YozakuraRuntime.playerStateManager.placing) {
                                    swap = true;
                                }
                                YozakuraRuntime.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = true;
                                this.fakeBlockState = false;
                            } else {
                                YozakuraRuntime.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                        case 2:
                            if (this.hasValidTarget()) {
                                if (!YozakuraRuntime.playerStateManager.digging && !YozakuraRuntime.playerStateManager.placing) {
                                    switch (this.blockTick) {
                                        case 0:
                                            if (!this.isPlayerBlocking()) {
                                                swap = true;
                                            }
                                            blocked = true;
                                            this.blockTick = 1;
                                            break;
                                        case 1:

                                            attack = false;
                                            this.blockTick = 2;
                                            break;
                                        case 2:
                                            if (this.isPlayerBlocking()) {
                                                if (!noSwap.getValue()) {
                                                    int randomSlot = new Random().nextInt(9);
                                                    while (randomSlot == mc.thePlayer.inventory.currentItem) {
                                                        randomSlot = new Random().nextInt(9);
                                                    }
                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                                }
                                                this.stopBlock();
                                            }
                                            attack = false;
                                            if (this.attackDelayMS <= 50L) {
                                                this.blockTick = 0;
                                            }
                                            break;
                                        default:
                                            this.blockTick = 0;
                                    }
                                }
                                this.isBlocking = true;
                                this.fakeBlockState = true;
                            } else {
                                YozakuraRuntime.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                int randomSlot = new Random().nextInt(9);
                                while (randomSlot == mc.thePlayer.inventory.currentItem) {
                                    randomSlot = new Random().nextInt(9);
                                }
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                        case 3:
                            if (this.hasValidTarget()) {
                                if (!YozakuraRuntime.playerStateManager.digging && !YozakuraRuntime.playerStateManager.placing) {
                                    switch (this.blockTick) {
                                        case 0:
                                            if (!this.isPlayerBlocking()) {
                                                swap = true;
                                            }
                                            this.blockTick = 1;
                                            break;
                                        case 1:
                                            if (this.isPlayerBlocking()) {
                                                this.stopBlock();
                                                attack = false;
                                            }
                                            if (this.attackDelayMS <= 50L) {
                                                this.blockTick = 0;
                                            }
                                            break;
                                        default:
                                            this.blockTick = 0;
                                    }
                                }
                                YozakuraRuntime.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = true;
                                this.fakeBlockState = false;
                            } else {
                                YozakuraRuntime.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                        case 4:
                            YozakuraRuntime.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                            this.isBlocking = false;
                            this.fakeBlockState = this.hasValidTarget();
                            if (PlayerUtil.isUsingItem()
                                    && !this.isPlayerBlocking()
                                    && !YozakuraRuntime.playerStateManager.digging
                                    && !YozakuraRuntime.playerStateManager.placing) {
                                swap = true;
                            }
                            break;
                        case 5:
                            if (this.hasValidTarget()) {
                                if (!YozakuraRuntime.playerStateManager.digging && !YozakuraRuntime.playerStateManager.placing) {
                                    switch (this.blockTick) {
                                        case 0:
                                            blocked = true;
                                            if (!this.isPlayerBlocking()) {
                                                swap = true;
                                            }
                                            this.blockTick = 1;
                                            break;
                                        case 1:
                                            if (isPlayerBlocking()) {
                                                int randomSlot = new Random().nextInt(9);
                                                while (randomSlot == mc.thePlayer.inventory.currentItem) {
                                                    randomSlot = new Random().nextInt(9);
                                                }
                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                            }
                                            attack = false;
                                            blockTick = 2;
                                            break;
                                        case 2:
                                            attack = false;
                                            this.stopBlock();
                                            if (this.attackDelayMS <= 50L) {
                                                this.blockTick = 0;
                                            }
                                            break;
                                        default:
                                            this.blockTick = 0;
                                    }
                                }
                                this.isBlocking = true;
                                this.fakeBlockState = true;
                            } else {
                                YozakuraRuntime.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                int randomSlot = new Random().nextInt(9);
                                while (randomSlot == mc.thePlayer.inventory.currentItem) {
                                    randomSlot = new Random().nextInt(9);
                                }
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                        case 6:
                            if (this.hasValidTarget()) {
                                if (!YozakuraRuntime.playerStateManager.digging && !YozakuraRuntime.playerStateManager.placing) {
                                    if (blockTick + 1 == startBlinkTick.getValue()){
                                        blocked = true;
                                    }
                                    if (blockTick + 1 != attackTick.getValue()){
                                        attack = false;
                                    }
                                    if (blockTick + 1 == startBlockTick.getValue()){
                                        if (!this.isPlayerBlocking()) {
                                            swap = true;
                                            if (postStartBlock.getValue())postBlock = true;
                                        }
                                    }
                                    if (blockTick + 1 == stopBlinkTick.getValue()){
                                        YozakuraRuntime.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                    }
                                    if (blockTick + 1 == swapTick.getValue()){
                                        int randomSlot = new Random().nextInt(9);
                                        while (randomSlot == mc.thePlayer.inventory.currentItem) {
                                            randomSlot = new Random().nextInt(9);
                                        }
                                        PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                                        swapped = true;
                                    }
                                    if (blockTick + 1 == switchBackTick.getValue()){
                                        if (swapped){
                                            PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                            swapped = false;
                                        }
                                    }
                                   if (blockTick + 1 == stopBlockTick.getValue()){
                                       if (this.isPlayerBlocking()) {
                                           this.stopBlock();
                                       }
                                   }
                                    blockTick++;
                                    if (blockTick >= maxTick.getValue() - 1){
                                        blockTick = 0;
                                    }
                                }
                                this.isBlocking = true;
                                this.fakeBlockState = true;
                            } else {
                                if (swapped){
                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                    swapped = false;
                                }
                                YozakuraRuntime.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                    }
                }
                boolean attacked = false;
                if (this.isBoxInSwingRange(this.attackTarget.getBox())) {
                    if (this.rotations.getValue() == 2 || this.rotations.getValue() == 3) {
                        float[] rotations = RotationUtil.getRotationsToBox(
                                this.attackTarget.getBox(),
                                event.getYaw(),
                                event.getPitch(),
                                (float) this.angleStep.getValue() + RandomUtil.nextFloat(-5.0F, 5.0F),
                                (float) this.smoothing.getValue() / 100.0F
                        );
                        event.setRotation(rotations[0], rotations[1], 1);
                        VisualRotationState.publish("KillAura", rotations[0], rotations[1], 1);
                        if (this.rotations.getValue() == 3) {
                            YozakuraRuntime.rotationManager.setRotation(rotations[0], rotations[1], 1, true);
                        }
                        if (this.moveFix.getValue() != 0 || this.rotations.getValue() == 3) {
                            event.setPervRotation(rotations[0], 1);
                        }
                    }
                    if (attack) {
                        attacked = this.performAttack(event.getNewYaw(), event.getNewPitch());
                    }
                }
                if (swap) {
                    if (attacked) {
                        this.interactAttack(event.getNewYaw(), event.getNewPitch());
                    } else {
                        if (!postBlock) this.sendUseItem();
                    }
                }
                if (blocked) {
                    YozakuraRuntime.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                    YozakuraRuntime.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                }
            }
        }
        if (event.getType() == EventType.POST && this.isEnabled()){
            if (postSwap){
                int randomSlot = new Random().nextInt(9);
                while (randomSlot == mc.thePlayer.inventory.currentItem) {
                    randomSlot = new Random().nextInt(9);
                }
                PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                mc.getNetHandler().addToSendQueue(new C17PacketCustomPayload("send", new PacketBuffer(Unpooled.buffer())));
                PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                this.stopBlock();
                postSwap = false;
            }
            if (postBlock){
                sendUseItem();
                postBlock = false;
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled()) {
            switch (event.getType()) {
                case PRE:
                    if (this.attackTarget == null
                            || !this.isValidTarget(this.attackTarget.getEntity())
                            || !this.isBoxInAttackRange(this.attackTarget.getBox())
                            || !this.isBoxInSwingRange(this.attackTarget.getBox())
                            || this.timer.hasTimeElapsed(this.switchDelay.getValue().longValue())) {
                        this.timer.reset();
                        ArrayList<EntityLivingBase> targets = new ArrayList<>();
                        for (Entity entity : mc.theWorld.loadedEntityList) {
                            if (entity instanceof EntityLivingBase
                                    && this.isValidTarget((EntityLivingBase) entity)
                                    && this.isInRange((EntityLivingBase) entity)) {
                                targets.add((EntityLivingBase) entity);
                            }
                        }
                        if (targets.isEmpty()) {
                            this.attackTarget = null;
                        } else {
                            if (targets.stream().anyMatch(this::isInSwingRange)) {
                                targets.removeIf(entityLivingBase -> !this.isInSwingRange(entityLivingBase));
                            }
                            if (targets.stream().anyMatch(this::isInAttackRange)) {
                                targets.removeIf(entityLivingBase -> !this.isInAttackRange(entityLivingBase));
                            }
                            if (targets.stream().anyMatch(this::isPlayerTarget)) {
                                targets.removeIf(entityLivingBase -> !this.isPlayerTarget(entityLivingBase));
                            }
                            targets.sort(
                                    (entityLivingBase1, entityLivingBase2) -> {
                                        int sortBase = 0;
                                        switch (this.sort.getValue()) {
                                            case 1:
                                                sortBase = Float.compare(TeamUtil.getHealthScore(entityLivingBase1), TeamUtil.getHealthScore(entityLivingBase2));
                                                break;
                                            case 2:
                                                sortBase = Integer.compare(entityLivingBase1.hurtResistantTime, entityLivingBase2.hurtResistantTime);
                                                break;
                                            case 3:
                                                sortBase = Float.compare(
                                                        RotationUtil.angleToEntity(entityLivingBase1),
                                                        RotationUtil.angleToEntity(entityLivingBase2)
                                                );
                                        }
                                        return sortBase != 0
                                                ? sortBase
                                                : Double.compare(RotationUtil.distanceToEntity(entityLivingBase1), RotationUtil.distanceToEntity(entityLivingBase2));
                                    }
                            );
                            if (this.mode.getValue() == 1 && this.hitRegistered) {
                                this.hitRegistered = false;
                                this.switchTick++;
                            }
                            if (this.mode.getValue() == 0 || this.switchTick >= targets.size()) {
                                this.switchTick = 0;
                            }
                            this.attackTarget = new AttackData(targets.get(this.switchTick));
                        }
                    }
                    if (this.attackTarget != null) {
                        this.attackTarget = new AttackData(this.attackTarget.getEntity());
                    }
                    break;
                case POST:
                    if (this.isPlayerBlocking() && !mc.thePlayer.isBlocking()) {
                        mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
                    }
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && !event.isCancelled()) {
            if (event.getPacket() instanceof C07PacketPlayerDigging) {
                C07PacketPlayerDigging packet = (C07PacketPlayerDigging) event.getPacket();
                if (packet.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
                    this.blockingState = false;
                }
            }
            if (event.getPacket() instanceof C09PacketHeldItemChange) {
                this.blockingState = false;
                if (this.isBlocking) {
                    mc.thePlayer.stopUsingItem();
                }
            }
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (this.isEnabled()) {
            if (this.moveFix.getValue() == 1
                    && this.rotations.getValue() != 3
                    && RotationState.isActived()
                    && RotationState.getPriority() == 1.0F
                    && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
            if (this.shouldAutoBlock()) {
                mc.thePlayer.movementInput.jump = false;
            }
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (this.isEnabled() && this.attackTarget != null) {
            if (this.showTarget.getValue() != 0
                    && TeamUtil.isEntityLoaded(this.attackTarget.getEntity())
                    && this.isAttackAllowed() && this.showTarget.getValue() != 3) {
                Color color = new Color(-1);
                switch (this.showTarget.getValue()) {
                    case 1:
                        if (this.attackTarget.getEntity().hurtTime > 0) {
                            color = new Color(16733525);
                        } else {
                            color = new Color(5635925);
                        }
                        break;
                    case 2:
                        color = ((HUD) YozakuraRuntime.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
                }
                RenderUtil.enableRenderState();
                try {
                    RenderUtil.drawEntityBox(this.attackTarget.getEntity(), color.getRed(), color.getGreen(), color.getBlue());
                } finally {
                    RenderUtil.disableRenderState();
                }
            }
            if (this.showTarget.getValue() == 3){
                renderScan(event);
            }
        }
    }
    public static Vec3 interpolate(Vec3 previousVec, Vec3 currentVec, float progress) {
        return new Vec3(
                previousVec.xCoord + (currentVec.xCoord - previousVec.xCoord) * progress,
                previousVec.yCoord + (currentVec.yCoord - previousVec.yCoord) * progress,
                previousVec.zCoord + (currentVec.zCoord - previousVec.zCoord) * progress
        );
    }

    private void renderScan(Render3DEvent event) {
        if (this.attackTarget == null) return;
        double renderPosX = mc.getRenderManager().viewerPosX;
        double renderPosY = mc.getRenderManager().viewerPosY;
        double renderPosZ = mc.getRenderManager().viewerPosZ;
        Vec3 interpolated = interpolate(new Vec3(this.attackTarget.entity.lastTickPosX, this.attackTarget.entity.lastTickPosY, this.attackTarget.entity.lastTickPosZ), this.attackTarget.entity.getPositionVector(), event.getPartialTicks());

        double height = this.attackTarget.entity.height;
        double offset = (Math.sin(System.currentTimeMillis() / 300.0) + 1) / 2.0 * height;

        double x = interpolated.xCoord - renderPosX;
        double y = interpolated.yCoord + offset - renderPosY;
        double z = interpolated.zCoord - renderPosZ;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_HINT_BIT | GL11.GL_LINE_BIT | GL11.GL_TEXTURE_BIT);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, z);

            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.disableAlpha();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.shadeModel(GL11.GL_SMOOTH);
            GlStateManager.disableCull();

            float radius = 0.6f;

            Tessellator tessellator = Tessellator.getInstance();
            WorldRenderer worldrenderer = tessellator.getWorldRenderer();
            worldrenderer.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);

            Color centerCol = ((HUD) YozakuraRuntime.moduleManager.modules.get(HUD.class)).getColor(0);
            worldrenderer.pos(0, 0, 0).color(centerCol.getRed()/255f, centerCol.getGreen()/255f, centerCol.getBlue()/255f, 0.4f).endVertex();

            for(int i = 0; i <= 360; i+=10) {
                double angle = Math.toRadians(i);
                Color edgeCol = ((HUD) YozakuraRuntime.moduleManager.modules.get(HUD.class)).getColor(i * 10);
                worldrenderer.pos(Math.sin(angle) * radius, 0, Math.cos(angle) * radius)
                        .color(edgeCol.getRed()/255f, edgeCol.getGreen()/255f, edgeCol.getBlue()/255f, 0.0f).endVertex();
            }
            tessellator.draw();

            GL11.glLineWidth(6.0f);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i <= 360; i+=5) {
                double angle = Math.toRadians(i);
                Color lineCol = ((HUD) YozakuraRuntime.moduleManager.modules.get(HUD.class)).getColor(i * 20);
                GL11.glColor4f(lineCol.getRed()/255f, lineCol.getGreen()/255f, lineCol.getBlue()/255f, 1.0f);
                GL11.glVertex3d(Math.sin(angle) * radius, 0, Math.cos(angle) * radius);
            }
            GL11.glEnd();
        } finally {
            GlStateManager.shadeModel(GL11.GL_FLAT);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
            GL11.glPopAttrib();
            gq.yozakura.engine.render.GLStateManager.syncToCurrent();
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && this.attackTarget != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && this.attackTarget != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && this.attackTarget != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onCancelUse(CancelUseEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onEnabled() {
        this.attackTarget = null;
        this.switchTick = 0;
        this.hitRegistered = false;
        this.attackDelayMS = 0L;
        this.blockTick = 0;
    }

    @Override
    public void onDisabled() {
        VisualRotationState.clearSource("KillAura");
        RotationDebug.setSourceEnabled("KillAura", false);
        YozakuraRuntime.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        this.blockingState = false;
        this.isBlocking = false;
        this.fakeBlockState = false;
    }

    @Override
    public void verifyValue(String mode) {
        if (!this.autoBlock.getName().equals(mode) && !this.autoBlockCPS.getName().equals(mode)) {
            if (this.swingRange.getName().equals(mode)) {
                if (this.swingRange.getValue() < this.attackRange.getValue()) {
                    this.attackRange.setValue(this.swingRange.getValue());
                }
            } else if (this.attackRange.getName().equals(mode)) {
                if (this.swingRange.getValue() < this.attackRange.getValue()) {
                    this.swingRange.setValue(this.attackRange.getValue());
                }
            } else if (this.minCPS.getName().equals(mode)) {
                if (this.minCPS.getValue() > this.maxCPS.getValue()) {
                    this.maxCPS.setValue(this.minCPS.getValue());
                }
            } else {
                if (this.maxCPS.getName().equals(mode) && this.minCPS.getValue() > this.maxCPS.getValue()) {
                    this.minCPS.setValue(this.maxCPS.getValue());
                }
            }
        }
    }
    public static class RotationData{
        private final Vec3 eye;
        private final Vec3 hiteVec;
        private double distance;
        private final Vector2f roation;
        public Vector2f getRotation(){
            return roation;
        }

        public RotationData(Vec3 bestEye, Vec3 bestHitVec, double minDistance, Vector2f bestRotation) {
            eye  = bestEye;
            hiteVec = bestHitVec;
            distance = minDistance;
            roation = bestRotation;
        }
    }
    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }

    public static class AttackData {
        private final EntityLivingBase entity;
        private final AxisAlignedBB box;
        private final double x;
        private final double y;
        private final double z;

        public AttackData(EntityLivingBase entityLivingBase) {
            this.entity = entityLivingBase;
            double collisionBorderSize = entityLivingBase.getCollisionBorderSize();
            this.box = entityLivingBase.getEntityBoundingBox().expand(collisionBorderSize, collisionBorderSize, collisionBorderSize);
            this.x = entityLivingBase.posX;
            this.y = entityLivingBase.posY;
            this.z = entityLivingBase.posZ;
        }

        public EntityLivingBase getEntity() {
            return this.entity;
        }

        public AxisAlignedBB getBox() {
            return this.box;
        }

        public double getX() {
            return this.x;
        }

        public double getY() {
            return this.y;
        }

        public double getZ() {
            return this.z;
        }
    }
}
