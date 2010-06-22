/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package client;

import constants.InventoryConstants;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import server.MapleItemInformationProvider;
import tools.DatabaseConnection;
import tools.MaplePacketCreator;

/**
 *
 * @author Matze
 */
public class MapleInventory implements Iterable<IItem> {
    private Map<Byte, IItem> inventory = new LinkedHashMap<Byte, IItem>();
    private byte slotLimit = 96;
    private MapleInventoryType type;

    public MapleInventory(MapleInventoryType type) {
        this.inventory = new LinkedHashMap<Byte, IItem>();
        this.type = type;
    }

    public void loadInventory(MapleCharacter player) throws SQLException {
        loadInventory(player, (byte) (player.gmLevel() > 0 ? 96 : 24));
    }

    public void loadInventory(MapleCharacter player, byte slots) throws SQLException {
        this.slotLimit = slots;
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps;
        if (isEquipInventory()) {
            ps = con.prepareStatement("SELECT * FROM inventoryitems LEFT JOIN inventoryequipment USING (inventoryitemid) WHERE characterid = ? AND inventorytype = ?");
        } else {
            ps = con.prepareStatement("SELECT * FROM inventoryitems WHERE characterid = ? AND inventorytype = ?");
        }
        ps.setInt(1, player.getId());
        ps.setInt(2, type.getType()); // Yes kill me, we're loading each invent separately
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            long currenttime = System.currentTimeMillis();
            long expiration = rs.getLong("expiredate");
            Item toAdd;
            if (isEquipInventory()) {
                int itemid = rs.getInt("itemid");
                int ringId = rs.getInt("ringid");
                if (ringId > 0 && type.equals(MapleInventoryType.EQUIPPED)) {
                    MapleRing ring = MapleRing.loadFromDb(ringId);
                    if (ring != null) {
                        if (itemid >= 1112001 && itemid <= 1112006) {
                            player.addCrushRing(ring);
                        } else if (itemid >= 1112800 && itemid <= 1112802) {
                            player.addFriendshipRing(ring);
                        } else if (itemid >= 1112803 && itemid <= 1112807 || itemid == 1112809) {
                            player.addMarriageRing(ring);
                        }
                    }
                }
                Equip equip = new Equip(itemid, (byte) rs.getInt("position"), rs.getInt("ringid"));
                equip.setOwner(rs.getString("owner"));
                equip.setQuantity((short) rs.getInt("quantity"));
                equip.setAcc((short) rs.getInt("acc"));
                equip.setAvoid((short) rs.getInt("avoid"));
                equip.setDex((short) rs.getInt("dex"));
                equip.setHands((short) rs.getInt("hands"));
                equip.setHp((short) rs.getInt("hp"));
                equip.setInt((short) rs.getInt("int"));
                equip.setJump((short) rs.getInt("jump"));
                equip.setVicious((short) rs.getInt("vicious"));
                equip.setFlag((byte) rs.getInt("flag"));
                equip.setLuk((short) rs.getInt("luk"));
                equip.setMatk((short) rs.getInt("matk"));
                equip.setMdef((short) rs.getInt("mdef"));
                equip.setMp((short) rs.getInt("mp"));
                equip.setSpeed((short) rs.getInt("speed"));
                equip.setStr((short) rs.getInt("str"));
                equip.setWatk((short) rs.getInt("watk"));
                equip.setWdef((short) rs.getInt("wdef"));
                equip.setUpgradeSlots((byte) rs.getInt("upgradeslots"));
                equip.setLevel((byte) rs.getInt("level"));
                equip.setItemExp(rs.getInt("itemexp"));
                toAdd = equip;
            } else {
                Item item = new Item(rs.getInt("itemid"), (byte) rs.getInt("position"), (short) rs.getInt("quantity"), rs.getInt("petid"));
                item.setOwner(rs.getString("owner"));
                toAdd = item;
            }
            if (expiration != -1) {
                if (currenttime < expiration) {
                    toAdd.setExpiration(expiration);
                } else {
                    // can't we send the expireditem packet ?
                    player.getClient().getSession().write(MaplePacketCreator.serverNotice(5, MapleItemInformationProvider.getInstance().getName(toAdd.getItemId()) + " has expired from your inventory."));
                    continue;
                }
            }
            addFromDB(toAdd);
        }
        rs.close();
        ps.close();
    }

    public void saveInventory(MapleCharacter player) throws SQLException {
        // cbfff...
    }

    public boolean isExtendableInventory() { // not sure about cash, basing this on the previous one.
        return !(type.equals(MapleInventoryType.UNDEFINED) || type.equals(MapleInventoryType.EQUIPPED) || type.equals(MapleInventoryType.CASH));
    }

    public boolean isEquipInventory() {
        return type.equals(MapleInventoryType.EQUIP) || type.equals(MapleInventoryType.EQUIPPED);
    }

    public byte getSlotLimit() {
        return slotLimit;
    }

    public void increaseSlotLimit(byte amount) {
        if (!isExtendableInventory()) {
            throw new RuntimeException("Extending a non extendable inventory."); // might need to unstuck people :|
        }
        slotLimit += amount;
    }

    public IItem findById(int itemId) {
        for (IItem item : inventory.values()) {
            if (item.getItemId() == itemId) {
                return item;
            }
        }
        return null;
    }

    public int countById(int itemId) {
        int possesed = 0;
        for (IItem item : inventory.values()) {
            if (item.getItemId() == itemId) {
                possesed += item.getQuantity();
            }
        }
        return possesed;
    }

    public List<IItem> listById(int itemId) {
        List<IItem> ret = new ArrayList<IItem>();
        for (IItem item : inventory.values()) {
            if (item.getItemId() == itemId) {
                ret.add(item);
            }
        }
        if (ret.size() > 1) {
            Collections.sort(ret);
        }
        return ret;
    }

    public Collection<IItem> list() {
        return inventory.values();
    }

    public byte addItem(IItem item) {
        byte slotId = getNextFreeSlot();
        if (slotId < 0) {
            return -1;
        }
        inventory.put(slotId, item);
        item.setPosition(slotId);
        return slotId;
    }

    public void addFromDB(IItem item) {
        if (item.getPosition() < 0 && !type.equals(MapleInventoryType.EQUIPPED)) {
            throw new RuntimeException("Item with negative position in non-equipped IV wtf?");
        }
        inventory.put(item.getPosition(), item);
    }

    public void move(byte sSlot, byte dSlot, short slotMax) {
        Item source = (Item) inventory.get(sSlot);
        Item target = (Item) inventory.get(dSlot);
        if (source == null) {
            throw new RuntimeException("Trying to move empty slot");
        }
        if (target == null) {
            source.setPosition(dSlot);
            inventory.put(dSlot, source);
            inventory.remove(sSlot);
        } else if (target.getItemId() == source.getItemId() && !InventoryConstants.isRechargable(source.getItemId())) {
            if (type.getType() == MapleInventoryType.EQUIP.getType()) {
                swap(target, source);
            }
            if (source.getQuantity() + target.getQuantity() > slotMax) {
                short rest = (short) ((source.getQuantity() + target.getQuantity()) - slotMax);
                source.setQuantity(rest);
                target.setQuantity(slotMax);
            } else {
                target.setQuantity((short) (source.getQuantity() + target.getQuantity()));
                inventory.remove(sSlot);
            }
        } else {
            swap(target, source);
        }
    }

    private void swap(IItem source, IItem target) {
        inventory.remove(source.getPosition());
        inventory.remove(target.getPosition());
        byte swapPos = source.getPosition();
        source.setPosition(target.getPosition());
        target.setPosition(swapPos);
        inventory.put(source.getPosition(), source);
        inventory.put(target.getPosition(), target);
    }

    public IItem getItem(byte slot) {
        return inventory.get(slot);
    }

    public void removeItem(byte slot) {
        removeItem(slot, (short) 1, false);
    }

    public void removeItem(byte slot, short quantity, boolean allowZero) {
        IItem item = inventory.get(slot);
        if (item == null) {// TODO is it ok not to throw an exception here?
            return;
        }
        item.setQuantity((short) (item.getQuantity() - quantity));
        if (item.getQuantity() < 0) {
            item.setQuantity((short) 0);
        }
        if (item.getQuantity() == 0 && !allowZero) {
            removeSlot(slot);
        }
    }

    public void removeSlot(byte slot) {
        inventory.remove(slot);
    }

    public boolean isFull() {
        return inventory.size() >= slotLimit;
    }

    public boolean isFull(int margin) {
        return inventory.size() + margin >= slotLimit;
    }

    public byte getNextFreeSlot() {
        if (isFull()) {
            return -1;
        }
        for (byte i = 1; i <= slotLimit; i++) {
            if (!inventory.keySet().contains(i)) {
                return i;
            }
        }
        return -1;
    }

    public MapleInventoryType getType() {
        return type;
    }

    @Override
    public Iterator<IItem> iterator() {
        return Collections.unmodifiableCollection(inventory.values()).iterator();
    }

        public IItem findByCashId(int cashId) {
	      for (IItem item : inventory.values()) {
	           if ((item.getPetId() > -1 ? item.getPetId() : item.getCashId()) == cashId)
	                return item;
	           }

	      return null;
	}
}