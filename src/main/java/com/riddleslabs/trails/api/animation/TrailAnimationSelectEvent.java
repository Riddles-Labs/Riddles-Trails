package com.riddleslabs.trails.api.animation;

import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;

/** Fired before each frame. Listeners may select an animation/style or cancel just this frame.
 * On Folia this is an asynchronous event on the player's owning entity thread: access only that region.
 */
public final class TrailAnimationSelectEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final TrailKind kind;
    private final String source;
    private String animationId;
    private AnimationStyle style;
    private boolean cancelled;
    private boolean styleChanged;
    public TrailAnimationSelectEvent(Player player, TrailKind kind, String source, String animationId, AnimationStyle style) {
        super(!Bukkit.isPrimaryThread());
        this.player=player; this.kind=kind; this.source=source; this.animationId=animationId; this.style=style;
    }
    public Player getPlayer() { return player; }
    public TrailKind getKind() { return kind; }
    public String getSource() { return source; }
    public String getAnimationId() { return animationId; }
    public void setAnimationId(String id) { animationId=Objects.requireNonNull(id); }
    public AnimationStyle getStyle() { return style; }
    public void setStyle(AnimationStyle style) { this.style=Objects.requireNonNull(style); styleChanged=true; }
    public boolean isStyleChanged() { return styleChanged; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled=cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
