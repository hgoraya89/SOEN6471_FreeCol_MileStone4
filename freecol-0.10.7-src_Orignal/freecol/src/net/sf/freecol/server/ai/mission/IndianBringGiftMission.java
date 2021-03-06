/**
 *  Copyright (C) 2002-2012   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.server.ai.mission;

import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Tension;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.pathfinding.CostDeciders;
import net.sf.freecol.server.ai.AIMain;
import net.sf.freecol.server.ai.AIMessage;
import net.sf.freecol.server.ai.AIUnit;


/**
 * Mission for bringing a gift to a specified player.
 *
 * The mission has three different tasks to perform:
 * <ol>
 * <li>Get the gift (goods) from the {@link IndianSettlement} that owns the
 * unit.
 * <li>Transport this gift to the given {@link Colony}.
 * <li>Complete the mission by delivering the gift.
 * </ol>
 */
public class IndianBringGiftMission extends Mission {

    private static final Logger logger = Logger.getLogger(IndianBringGiftMission.class.getName());

    /** The tag for this mission. */
    private static final String tag = "AI native gifter";

    /**
     * The Colony to receive the gift.
     */
    private Location target = null;

    /** Decides whether this mission has been completed or not. */
    private boolean completed;


    /**
     * Creates a mission for the given <code>AIUnit</code>.
     *
     * @param aiMain The main AI-object.
     * @param aiUnit The <code>AIUnit</code> this mission is created for.
     * @param target The <code>Colony</code> receiving the gift.
     */
    public IndianBringGiftMission(AIMain aiMain, AIUnit aiUnit, Colony target) {
        super(aiMain, aiUnit);

        this.target = target; // Sole place target is to be set.
        this.completed = false;

        Unit unit = getUnit();
        if (!unit.getOwner().isIndian() || !unit.canCarryGoods()) {
            throw new IllegalArgumentException("Unsuitable unit: " + unit);
        }
        uninitialized = false;
    }

    /**
     * Creates a new <code>IndianBringGiftMission</code> and reads the given
     * element.
     *
     * @param aiMain The main AI-object.
     * @param aiUnit The <code>AIUnit</code> this mission is created for.
     * @param in The input stream containing the XML.
     * @throws XMLStreamException if a problem was encountered during parsing.
     * @see net.sf.freecol.server.ai.AIObject#readFromXML
     */
    public IndianBringGiftMission(AIMain aiMain, AIUnit aiUnit,
                                  XMLStreamReader in)
        throws XMLStreamException {
        super(aiMain, aiUnit);

        readFromXML(in);
        uninitialized = getAIUnit() == null;
    }


    /**
     * Checks if the unit is carrying a gift (goods).
     *
     * @return True if the unit is carrying goods.
     */
    private boolean hasGift() {
        return getUnit().hasGoodsCargo();
    }


    // Mission interface

    /**
     * {@inheritDoc}
     */
    public Location getTarget() {
        return target;
    }

    /**
     * {@inheritDoc}
     */
    public void setTarget(Location target) {
        throw new IllegalStateException("Target is fixed");
    }

    /**
     * {@inheritDoc}
     */
    public Location findTarget() {
        throw new IllegalStateException("Target is fixed");
    }

    /**
     * Why would this mission be invalid with the given unit?
     *
     * @param aiUnit The <code>AIUnit</code> to test.
     * @return A reason why the mission would be invalid with the unit,
     *     or null if none found.
     */
    private static String invalidMissionReason(AIUnit aiUnit) {
        String reason = invalidAIUnitReason(aiUnit);
        return (reason != null)
            ? reason
            : (aiUnit.getUnit().getIndianSettlement() == null)
            ? "home-destroyed"
            : null;
    }

    /**
     * Why would an IndianBringGiftMission be invalid with the given
     * unit and colony.
     *
     * @param aiUnit The <code>AIUnit</code> to test.
     * @param colony The <code>Colony</code> to test.
     * @return A reason why the mission would be invalid with the unit
     *     and colony or null if none found.
     */
    private static String invalidColonyReason(AIUnit aiUnit, Colony colony) {
        String reason = invalidTargetReason(colony);
        if (reason != null) return reason;
        final Unit unit = aiUnit.getUnit();
        final Player owner = unit.getOwner();
        Player targetPlayer = colony.getOwner();
        switch (owner.getStance(targetPlayer)) {
        case UNCONTACTED: case WAR: case CEASE_FIRE:
            return "bad-stance";
        case PEACE: case ALLIANCE:
            Tension tension = unit.getIndianSettlement()
                .getAlarm(targetPlayer);
            if (tension != null && tension.getLevel()
                .compareTo(Tension.Level.HAPPY) > 0) return "unhappy";
        }
        return null;
    }

    /**
     * Why would this mission be invalid with the given AI unit?
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @return A reason for mission invalidity, or null if none found.
     */
    public static String invalidReason(AIUnit aiUnit) {
        return invalidMissionReason(aiUnit);
    }

    /**
     * Why would this mission be invalid with the given AI unit and location?
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @param loc The <code>Location</code> to check.
     * @return A reason for invalidity, or null if none found.
     */
    public static String invalidReason(AIUnit aiUnit, Location loc) {
        String reason = invalidMissionReason(aiUnit);
        return (reason != null) ? reason
            : (loc instanceof Colony)
            ? invalidColonyReason(aiUnit, (Colony)loc)
            : Mission.TARGETINVALID;
    }

    /**
     * {@inheritDoc}
     */
    public String invalidReason() {
        String reason = invalidReason(getAIUnit(), target);
        return (reason != null) ? reason
            : (completed)
            ? "completed"
            : null;
    }

    // Not a one-time mission, omit isOneTime().

    /**
     * {@inheritDoc}
     */
    public void doMission() {
        String reason = invalidReason();
        if (reason != null) {
            logger.finest(tag + " broken(" + reason + "): " + this);
            return;
        }

        final AIUnit aiUnit = getAIUnit();
        final Unit unit = getUnit();
        final IndianSettlement is = unit.getIndianSettlement();
        if (!hasGift()) {
            Unit.MoveType mt = travelToTarget(tag, is, null);
            switch (mt) {
            case MOVE_NO_MOVES:
                return;
            case MOVE: // Arrived!
                break;
            case ATTACK_SETTLEMENT: case ATTACK_UNIT: // A blockage!
                Location blocker = resolveBlockage(aiUnit, is);
                if (blocker == null) {
                    logger.warning(tag + " could not resolve blockage"
                        + ": " + this);
                    moveRandomly(tag, null);
                    unit.setMovesLeft(0);
                } else {
                    logger.finest(tag + " blocked by " + blocker
                        + ", attacking: " + this);
                    AIMessage.askAttack(aiUnit, unit.getTile()
                        .getDirection(blocker.getTile()));
                }
                break;
            default:
                logger.warning(tag + " unexpected collection move type: " + mt
                    + ": " + this);
                moveRandomly(tag, null);
                return;
            }
            // Load the goods.
            Goods gift = is.getRandomGift(getAIRandom());
            if (gift == null) {
                logger.finest(tag + " found no gift to collect"
                    + " at " + is.getName() + ": " + this);
            } else {
                if (!AIMessage.askLoadCargo(aiUnit, gift)) {
                    logger.finest(tag + " failed to collect gift "
                        + " at " + is.getName() + ": " + this);
                }
            }
            if (!hasGift()) {
                completed = true;
                return;
            }
        } else {
            // Move to the target's colony and deliver, avoiding trouble
            // by choice of cost decider.
            Unit.MoveType mt = travelToTarget(tag, getTarget(),
                CostDeciders.avoidSettlementsAndBlockingUnits());
            switch (mt) {
            case MOVE_NO_MOVES:
                return;
            case ATTACK_SETTLEMENT: // Arrived (do not really attack)
                break;
            default:
                logger.finest(tag + " unexpected delivery move type: " + mt
                    + ": " + this);
                moveRandomly(tag, null);
                return;
            }

            if (!unit.getTile().isAdjacent(getTarget().getTile())) {
                throw new IllegalStateException("Not at target: "
                    + getTarget());
            }
            Settlement settlement = (Settlement)getTarget();
            if (AIMessage.askGetTransaction(aiUnit, settlement)
                && AIMessage.askDeliverGift(aiUnit, settlement,
                    unit.getGoodsList().get(0))) {
                AIMessage.askCloseTransaction(aiUnit, settlement);
                logger.finest(tag + " completed at " + settlement.getName()
                    + ": " + this);
            } else {
                logger.warning(tag + " failed at " + settlement.getName()
                    + ": " + this);
            }
            completed = true;
        }
    }


    // Serialization

    /**
     * {@inheritDoc}
     */
    @Override
    protected void toXMLImpl(XMLStreamWriter out) throws XMLStreamException {
        if (isValid()) {
            toXML(out, getXMLElementTagName());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeAttributes(XMLStreamWriter out)
        throws XMLStreamException {
        super.writeAttributes(out);

        if (target != null) {
            out.writeAttribute("target", target.getId());
        }

        out.writeAttribute("completed", Boolean.toString(completed));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readAttributes(XMLStreamReader in)
        throws XMLStreamException {
        super.readAttributes(in);

        String str = in.getAttributeValue(null, "target");
        target = getGame().getFreeColGameObject(str, Colony.class);

        str = in.getAttributeValue(null, "completed");
        // @compat 0.9.x
        if (str == null) str = in.getAttributeValue(null, "giftDelivered");
        // end compatibility code
        completed = Boolean.valueOf(str).booleanValue();
    }

    /**
     * Gets the tag name of the root element representing this object.
     *
     * @return "indianBringGiftMission".
     */
    public static String getXMLElementTagName() {
        return "indianBringGiftMission";
    }
}
