package de.greenman999;

import net.minecraft.block.LecternBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillagerProfession;


public class TradeFinder {

    public static TradeState state = TradeState.IDLE;
    public static VillagerEntity villager = null;
    public static BlockPos lecternPos = null;

    public static int tries = 0;

    //public static int placeDelay = 3;
    //public static int interactDelay = 2;
    public static Vec3d prevPos = null;

    public static void stop() {
        state = TradeState.IDLE;
        villager = null;
        lecternPos = null;
        tries = 0;
    }

    public static void search() {
        state = TradeState.CHECK;
    }

    public static void select(VillagerEntity villagerEntity, BlockPos blockPos) {
        villager = villagerEntity;
        lecternPos = blockPos;
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if(state == TradeState.IDLE) return;
        ClientPlayerEntity player = mc.player;
        if(player == null) return;
        switch (state) {
            case CHECK -> mc.inGameHud.setOverlayMessage(Text.literal("checking trade --- attempt: " + tries).formatted(Formatting.GRAY), false);
            case BREAK -> mc.inGameHud.setOverlayMessage(Text.literal("breaking lectern --- attempt: " + tries).formatted(Formatting.GRAY), false);
            case PLACE -> mc.inGameHud.setOverlayMessage(Text.literal("placing lectern --- attempt: " + tries).formatted(Formatting.GRAY), false);
        }

        if((state == TradeState.CHECK || state == TradeState.WAITING_FOR_PACKET) && villager.getVillagerData().getProfession().equals(VillagerProfession.LIBRARIAN)) {
            Vec3d villagerPosition = new Vec3d(villager.getX(), villager.getY() + (double) villager.getEyeHeight(EntityPose.STANDING), villager.getZ());

            if(LibrarianTradeFinder.getConfig().legitMode) {
                player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, villagerPosition);
            }
            /*if(interactDelay > 0) {
                interactDelay--;
                return;
            }
            interactDelay = 2;*/

            ActionResult result = null;
            if (mc.interactionManager != null) {
                result = mc.interactionManager.interactEntity(mc.player, villager, Hand.MAIN_HAND);
            }
            if(result == ActionResult.SUCCESS) {
                state = TradeState.WAITING_FOR_PACKET;
            }else {
                mc.inGameHud.getChatHud().addMessage(Text.literal("Failed to interact with villager. Try again.").styled(style -> style.withColor(TextColor.fromFormatting(Formatting.RED))));
                stop();
            }

        } else if(state == TradeState.BREAK) {
            BlockPos toPlace = lecternPos.down();
            if(LibrarianTradeFinder.getConfig().legitMode) {
                player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, new Vec3d(toPlace.getX() + 0.5, toPlace.getY() + 1.0, toPlace.getZ() + 0.5));
            }
            PlayerInventory inventory = player.getInventory();
            ItemStack mainHand = inventory.getMainHandStack();
            if(mainHand.getItem() instanceof AxeItem) {
                int remainingDurability = mainHand.getMaxDamage() - mainHand.getDamage();
                if(remainingDurability <= 5 && LibrarianTradeFinder.getConfig().preventAxeBreaking) {
                    stop();
                    mc.inGameHud.getChatHud().addMessage(Text.literal("The searching process was stopped because your axe is about to break.").formatted(Formatting.RED));
                    return;
                }
            }
            if (mc.world != null && mc.world.getBlockState(lecternPos).getBlock() instanceof LecternBlock && mc.interactionManager != null) {
                player.swingHand(Hand.MAIN_HAND, true);
                mc.interactionManager.updateBlockBreakingProgress(lecternPos, Direction.UP);
                player.networkHandler
                        .sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }else {
                state = TradeState.PLACE;
                if(LibrarianTradeFinder.getConfig().tpToVillager && mc.getNetworkHandler() != null) {
                    prevPos = mc.player.getPos();
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(villager.getX(), villager.getY(), villager.getZ(), true));
                }
            }

        } else if (state == TradeState.PLACE) {
            if(LibrarianTradeFinder.getConfig().tpToVillager && mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(prevPos.x, prevPos.y, prevPos.z, true));
            }

            BlockPos toPlace = lecternPos.down();
            if(LibrarianTradeFinder.getConfig().legitMode) {
                mc.player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, new Vec3d(toPlace.getX() + 0.5, toPlace.getY() + 1.0, toPlace.getZ() + 0.5));
            }

            if(!mc.player.getOffHandStack().getItem().equals(Items.LECTERN) && mc.interactionManager != null) {
                if (mc.player.playerScreenHandler == mc.player.currentScreenHandler) {
                    for (int i = 9; i < 45; i++) {
                        if (mc.player.getInventory().getStack(i >= 36 ? i - 36 : i).getItem() == Items.LECTERN) {
                            boolean itemInOffhand = !mc.player.getOffHandStack().isEmpty();
                            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, mc.player);

                            if (itemInOffhand)
                                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, i, 0, SlotActionType.PICKUP, mc.player);

                            break;
                        }
                    }
                } else {
                    for (int i = 0; i < 9; i++) {
                        if (mc.player.getInventory().getStack(i).getItem() == Items.LECTERN) {
                            if (i != mc.player.getInventory().selectedSlot) {
                                mc.player.getInventory().selectedSlot = i;
                                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(i));
                            }

                            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                            break;
                        }
                    }
                }
            }

            /*if(placeDelay > 0) {
                placeDelay--;
                return;
            }
            placeDelay = 3;*/
            BlockHitResult hit = new BlockHitResult(new Vec3d(lecternPos.getX(), lecternPos.getY(),
                    lecternPos.getZ()), Direction.UP, lecternPos.down(), false);
            if (mc.interactionManager != null) {
                mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, hit);
            }

            state = TradeState.CHECK;
        }
    }
}
