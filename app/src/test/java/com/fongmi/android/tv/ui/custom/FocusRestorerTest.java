package com.fongmi.android.tv.ui.custom;

import androidx.recyclerview.widget.RecyclerView;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FocusRestorerTest {

    @Test
    public void removedItemKeepsFocusOnTheSameSlotWhenLaterItemsShiftUp() {
        // 3 列，删掉第 0 行第 1 列（下标 1），后面的条目补位，焦点留在原位
        assertEquals(1, FocusRestorer.nextPosition(1, 5, 3));
        assertEquals(0, FocusRestorer.nextPosition(0, 5, 3));
    }

    @Test
    public void removedLastItemOfARowFallsBackWithinTheSameRow() {
        // 3 列 5 条：删掉下标 4（第 1 行第 1 列，行内末位）后剩 4 条，退到新末位 3——仍在第 1 行
        assertEquals(3, FocusRestorer.nextPosition(4, 4, 3));
    }

    @Test
    public void removedOnlyItemOfTheLastRowMovesUpOneRowKeepingTheColumn() {
        // 3 列 4 条：下标 3 是第 1 行唯一条目，删除后整行消失。
        // 一维 clamp 会给出 2（第 0 行第 2 列，横跨整屏），正确目标是同列上一行的 0
        assertEquals(0, FocusRestorer.nextPosition(3, 3, 3));
        // 3 列 7 条：下标 6 独占第 2 行第 0 列，删除后回到第 1 行第 0 列
        assertEquals(3, FocusRestorer.nextPosition(6, 6, 3));
    }

    @Test
    public void nextPositionNeverEscapesTheNewListBounds() {
        // 不只覆盖「删一条」，也覆盖聚合删除后一次少掉多条的情况
        for (int span = 1; span <= 6; span++) {
            for (int position = 0; position <= 40; position++) {
                for (int count = 0; count <= 40; count++) {
                    int next = FocusRestorer.nextPosition(position, count, span);
                    if (count == 0) {
                        assertEquals("空列表必须返回 NO_POSITION", RecyclerView.NO_POSITION, next);
                        continue;
                    }
                    assertTrue("span=" + span + " count=" + count + " position=" + position
                            + " 返回了越界下标 " + next, next >= 0 && next < count);
                    // 焦点只允许留在原位或往前退，绝不能往后跳到用户没去过的条目
                    assertTrue("span=" + span + " count=" + count + " position=" + position
                            + " 让焦点前进到了 " + next, next <= position);
                    boolean clamped = position >= count && position - span > count - 1;
                    if (position >= count && !clamped && (count - 1) / span != position / span) {
                        assertEquals("span=" + span + " count=" + count + " position=" + position
                                + " 整行删空后必须保持同列", position % span, next % span);
                    }
                }
            }
        }
    }

    @Test
    public void emptyOrUnknownPositionYieldsNoPosition() {
        assertEquals(RecyclerView.NO_POSITION, FocusRestorer.nextPosition(RecyclerView.NO_POSITION, 5, 3));
        assertEquals(RecyclerView.NO_POSITION, FocusRestorer.nextPosition(0, 0, 3));
    }

    @Test
    public void singleColumnListBehavesLikeALinearList() {
        assertEquals(2, FocusRestorer.nextPosition(2, 5, 1));
        assertEquals(3, FocusRestorer.nextPosition(4, 4, 1));
    }

    @Test
    public void restoreGuardsAgainstHiddenListsAndScrollsToUnlaidOutTargets() throws Exception {
        String source = read(findMainJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "custom", "FocusRestorer.java")));
        assertTrue("恢复焦点前必须确认列表可见，否则 requestFocus 会被 INVISIBLE 的祖先拒绝",
                source.contains("!recycler.isShown()"));
        assertTrue("目标行未 layout 时必须先滚动，并再等一次 pre-draw 才能拿到 holder（post 会跑在 layout 之前）",
                source.contains("recycler.scrollToPosition(position);")
                        && source.contains("schedule(recycler, position, scheduled, false);"));
        assertFalse("不能用 post 做滚动重试：它的消息会在重新 layout 之前执行，重试必然拿不到 holder",
                source.contains("recycler.post("));
        assertTrue("重试仍失败时必须把焦点兜底交回列表本身",
                source.contains("recycler.requestFocus()"));
        assertTrue("必须检查 requestFocus 的返回值，而不是假定成功",
                source.contains("return holder != null && holder.itemView.requestFocus();"));
        assertTrue("排队的恢复必须可取消，避免退出删除模式或离开页面后抢焦点",
                source.contains("public void cancel()")
                        && source.contains("pending.removeListener();"));
        assertTrue("滚动重试不受 removeListener 管辖，必须用代际号让 cancel 也能作废它",
                source.contains("if (scheduled != generation) return;"));
    }

    @Test
    public void leanbackHistoryPageRestoresFocusByItemIdentityAndCancelsOnExit() throws Exception {
        String source = read(findLeanbackJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "HistoryActivity.java")));
        assertTrue("删除单条后必须按网格规则恢复焦点，而不是一维 clamp",
                source.contains("FocusRestorer.nextPosition(target, mAdapter.getItemCount(), Product.getColumn())"));
        assertFalse("不能对网格下标做一维 clamp——列删空时会把焦点甩到上一行另一端",
                source.contains("mAdapter.getItemCount() - 1)"));
        assertTrue("删除位置必须来自 holder 的绑定下标，而不是重新 indexOf 扫描",
                source.contains("public void onItemDelete(History item, int position)"));
        assertTrue("去抖刷新会重排列表，恢复焦点必须按条目身份重新定位",
                source.contains("indexOf(focused)"));
        assertTrue("聚焦条目已不在新列表时必须退回原位置，而不是放弃恢复让焦点掉给删除按钮",
                source.contains("reposition(focused, position)")
                        && source.contains("if (found >= 0) return found;"));
        assertTrue("holder 绑定下标失效时必须退回当前聚焦位置",
                source.contains("position >= 0 ? position : focusedPosition()"));
        assertTrue("恢复焦点前必须做生命周期守卫",
                source.contains("if (isFinishing() || isDestroyed()) return;"));
        assertTrue("退出删除模式、返回与 onStop 都必须取消排队中的恢复",
                source.contains("mFocusRestorer.cancel();")
                        && source.contains("protected void onStop()"));
    }

    @Test
    public void leanbackHistoryAdapterPassesTheBoundPositionOnDelete() throws Exception {
        String source = read(findLeanbackJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "adapter", "HistoryAdapter.java")));
        assertTrue("删除回调必须带上 holder 的绑定下标",
                source.contains("void onItemDelete(History item, int position);")
                        && source.contains("listener.onItemDelete(item, holder.getBindingAdapterPosition());"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static Path findLeanbackJavaPath() {
        Path moduleRelative = Path.of("src", "leanback", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "java");
    }
}
