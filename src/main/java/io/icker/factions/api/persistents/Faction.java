package io.icker.factions.api.persistents;

import io.icker.factions.FactionsMod;
import io.icker.factions.api.events.FactionEvents;
import io.icker.factions.database.Database;
import io.icker.factions.database.Field;
import io.icker.factions.database.Name;
import io.icker.factions.util.Message;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static io.icker.factions.api.events.FactionEvents.*;

@Name("Faction")
public class Faction {
    //region Constants
    public static final String DEFAULT_DESCRIPTION = "No description set";
    public static final String DEFAULT_MOTD = "No faction MOTD set";

    private static final HashMap<UUID, Faction> STORE = Database.load(Faction.class, Faction::getID);
    //endregion


    @Field("ID")
    private final UUID id;
    @Field("Invites")
    public ArrayList<UUID> invites = new ArrayList<>();
    @Field("Name")
    private String name;

    @Field("Description")
    private String description;

    @Field("MOTD")
    private String motd;

    @Field("Color")
    private String color;

    @Field("Open")
    private boolean open;

    @Field("Power")
    private int power;

    @Field("Home")
    private Home home;

    @Field("Safe")
    private SimpleInventory safe = new SimpleInventory(54);
    
    @Field("Relationships")
    private ArrayList<Relationship> relationships = new ArrayList<>();

    public Faction(@NotNull final String name,
                   @NotNull final String description,
                   @NotNull final String motd,
                   @NotNull final Formatting color,
                   final boolean open,
                   final int power) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.motd = motd;
        this.description = description;
        this.color = color.getName();
        this.open = open;
        this.power = power;
    }

//    NOTE(CamperSamu): Why does this exist?
//    public Faction() {}

    public static @Nullable Faction get(@NotNull final UUID id) {
        return STORE.get(id);
    }

    public static @Nullable Faction getByName(@NotNull final String name) {
        return STORE.values()
                .stream()
                .filter(f -> f.name.equals(name))
                .findFirst()
                .orElse(null);
    }

    public static void add(@NotNull final Faction faction) {
        STORE.put(faction.id, faction);
    }

    @Contract(pure = true)
    public static @NotNull Collection<Faction> all() {
        return STORE.values();
    }

    @SuppressWarnings("unused") //Util method
    public static List<Faction> allBut(UUID id) {
        return STORE.values()
                .stream()
                .filter(f -> f.id != id)
                .toList();
    }

    public static void save() {
        Database.save(Faction.class, STORE.values().stream().toList());
    }

    @SuppressWarnings("unused") //Util method
    public @NotNull String getKey() {
        return id.toString();
    }

    public @NotNull UUID getID() {
        return id;
    }

    public @NotNull String getName() {
        return name;
    }

    public void setName(@NotNull final String name) {
        this.name = name;
        MODIFY.invoker().onModify(this);
    }

    public @NotNull Message getTruncatedName() {
        boolean overLength = FactionsMod.CONFIG.NAME_MAX_LENGTH > -1 && name.length() > FactionsMod.CONFIG.NAME_MAX_LENGTH;
        Message displayName = new Message(overLength ? name.substring(0, FactionsMod.CONFIG.NAME_MAX_LENGTH - 1) + "..." : name);
        if (overLength) {
            displayName = displayName.hover(name);
        }
        return displayName;
    }

    public @NotNull Formatting getColor() {
        final var c = Formatting.byName(color);
        return c != null ? c : Formatting.WHITE;
    }

    public void setColor(final Formatting color) {
        if (color != null) this.color = color.getName();
        else this.color = Formatting.WHITE.getName();
        MODIFY.invoker().onModify(this);
    }

    public @NotNull String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        if (description != null) this.description = description;
        else this.description = DEFAULT_DESCRIPTION;
        MODIFY.invoker().onModify(this);
    }

    public @NotNull String getMOTD() {
        return motd;
    }

    public void setMOTD(final String motd) {
        if (motd != null) this.motd = motd;
        else this.motd = DEFAULT_MOTD;
        MODIFY.invoker().onModify(this);
    }

    public int getPower() {
        return power;
    }

    public @NotNull SimpleInventory getSafe() {
        return safe;
    }

    @SuppressWarnings("unused") //Util method
    public void setSafe(@NotNull final SimpleInventory safe) {
        this.safe = safe;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(final boolean open) {
        this.open = open;
        MODIFY.invoker().onModify(this);
    }

    public int adjustPower(final int adjustment) {
        int newPower = Math.min(Math.max(0, power + adjustment), calculateMaxPower());
        int oldPower = this.power;

        if (newPower == oldPower) return 0;

        power = newPower;
        FactionEvents.POWER_CHANGE.invoker().onPowerChange(this, oldPower);
        return Math.abs(newPower - oldPower);
    }

    public @NotNull List<User> getUsers() {
        return User.getByFaction(id);
    }

    public @NotNull List<Claim> getClaims() {
        return Claim.getByFaction(id);
    }

    public void removeAllClaims() {
        getClaims()
                .forEach(Claim::remove);
        REMOVE_ALL_CLAIMS.invoker().onRemoveAllClaims(this);
    }

    public void addClaim(final int x, final int z, @NotNull final String level) {
        Claim.add(new Claim(x, z, level, id));
    }

    public boolean isInvited(@NotNull final UUID playerID) {
        return invites.stream().anyMatch(invite -> invite.equals(playerID));
    }

    public @Nullable Home getHome() {
        return home;
    }

    public void setHome(@Nullable final Home home) {
        this.home = home;
        SET_HOME.invoker().onSetHome(this, home);
    }

    public Relationship getRelationship(UUID target) {
        return relationships.stream().filter(rel -> rel.target.equals(target)).findFirst().orElse(new Relationship(target, Relationship.Status.NEUTRAL));
    }

    public @NotNull Relationship getReverse(@NotNull Relationship rel) {
        final var target = Faction.get(rel.target);
        if (target != null) return target.getRelationship(id);
        else return new Relationship(id, Relationship.Status.NEUTRAL);
    }

    public boolean isMutualAllies(@NotNull final UUID target) {
        final Relationship rel = getRelationship(target);
        return rel.status == Relationship.Status.ALLY && getReverse(rel).status == Relationship.Status.ALLY;
    }

    public @NotNull List<Relationship> getMutualAllies() {
        return relationships.stream().filter(rel -> isMutualAllies(rel.target)).toList();
    }

    public @NotNull List<Relationship> getEnemiesWith() {
        return relationships.stream().filter(rel -> rel.status == Relationship.Status.ENEMY).toList();
    }

    public @NotNull List<Relationship> getEnemiesOf() {
        return relationships.stream().filter(rel -> getReverse(rel).status == Relationship.Status.ENEMY).toList();
    }

    public void removeRelationship(@NotNull final UUID target) {
        relationships = new ArrayList<>(relationships.stream().filter(rel -> !rel.target.equals(target)).toList());
    }

    public void setRelationship(@NotNull final Relationship relationship) {
        removeRelationship(relationship.target);
        if (relationship.status != Relationship.Status.NEUTRAL)
            relationships.add(relationship);
    }

    public void remove() {
        for (final User user : getUsers()) {
            user.leaveFaction();
        }
        for (final Relationship rel : relationships) {
            final var faction = Faction.get(rel.target);
            if (faction != null) faction.removeRelationship(id);
        }
        removeAllClaims();
        STORE.remove(id);
        DISBAND.invoker().onDisband(this);
    }

//  TODO(samu): import per-player power patch
    public int calculateMaxPower(){
        return FactionsMod.CONFIG.POWER.BASE + (getUsers().size() * FactionsMod.CONFIG.POWER.MEMBER);
    }
}