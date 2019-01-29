package com.carolinarollergirls.scoreboard.core.impl;
/**
 * Copyright (C) 2008-2012 Mr Temper <MrTemper@CarolinaRollergirls.com>
 *
 * This file is part of the Carolina Rollergirls (CRG) ScoreBoard.
 * The CRG ScoreBoard is licensed under either the GNU General Public
 * License version 3 (or later), or the Apache License 2.0, at your option.
 * See the file COPYING for details.
 */

import java.util.Comparator;
import java.util.Map;
import java.util.HashMap;

import com.carolinarollergirls.scoreboard.core.FloorPosition;
import com.carolinarollergirls.scoreboard.core.Position;
import com.carolinarollergirls.scoreboard.core.Role;
import com.carolinarollergirls.scoreboard.core.ScoreBoard;
import com.carolinarollergirls.scoreboard.core.Skater;
import com.carolinarollergirls.scoreboard.core.Team;
import com.carolinarollergirls.scoreboard.core.TeamJam;
import com.carolinarollergirls.scoreboard.event.ScoreBoardEventProviderImpl;
import com.carolinarollergirls.scoreboard.event.ScoreBoardEvent.AddRemoveProperty;
import com.carolinarollergirls.scoreboard.event.ScoreBoardEvent.CommandProperty;
import com.carolinarollergirls.scoreboard.event.ScoreBoardEvent.PermanentProperty;
import com.carolinarollergirls.scoreboard.event.ScoreBoardEvent.ValueWithId;
import com.carolinarollergirls.scoreboard.rules.Rule;

public class TeamImpl extends ScoreBoardEventProviderImpl implements Team {
    public TeamImpl(ScoreBoard sb, String i) {
	super(sb, ScoreBoard.Child.TEAM, Team.class, Value.class, Child.class);
        id = i;
	for (FloorPosition fp : FloorPosition.values()) {
            add(Child.POSITION, new PositionImpl(this, fp));
        }
	addReference(new IndirectPropertyReference(this, Value.SCORE, this, Value.RUNNING_OR_ENDED_TEAM_JAM,
		TeamJam.Value.TOTAL_SCORE, true, 0));
	addReference(new IndirectPropertyReference(this, Value.JAM_SCORE, this, Value.RUNNING_OR_ENDED_TEAM_JAM,
		TeamJam.Value.JAM_SCORE, false, 0));
	addReference(new IndirectPropertyReference(this, Value.LAST_SCORE, this, Value.RUNNING_OR_ENDED_TEAM_JAM,
		TeamJam.Value.LAST_SCORE, true, 0));
	addReference(new IndirectPropertyReference(this, Value.LEAD_JAMMER, this, Value.RUNNING_OR_ENDED_TEAM_JAM,
		TeamJam.Value.LEAD_JAMMER, false, LEAD_NO_LEAD));
	addReference(new IndirectPropertyReference(this, Value.NO_PIVOT, this, Value.RUNNING_OR_UPCOMING_TEAM_JAM,
		TeamJam.Value.NO_PIVOT, false, true));
	addReference(new IndirectPropertyReference(this, Value.STAR_PASS, this, Value.RUNNING_OR_UPCOMING_TEAM_JAM,
		TeamJam.Value.STAR_PASS, false, false));
    }

    public boolean set(PermanentProperty prop, Object value, Flag flag) {
	synchronized (coreLock) {
	    if (prop == Value.LAST_SCORE) {
		TeamJam cur = getRunningOrEndedTeamJam();
		TeamJam prev = cur.getPrevious();
		if (prev == null) { return false; }
		int change = (Integer)value;
		if (flag != Flag.CHANGE) {
		    change -= getLastScore();
		}
		change = Math.min(Math.max(-prev.getJamScore(), change), cur.getJamScore());
		if (change != 0) {
		    requestBatchStart();
			prev.set(TeamJam.Value.JAM_SCORE, change, Flag.CHANGE);
			cur.set(TeamJam.Value.JAM_SCORE, -change, Flag.CHANGE);
		    requestBatchEnd();
		    return true;
		}
		return false;
	    } else if (prop == Value.SCORE) {
		TeamJam cur = getRunningOrEndedTeamJam();
		TeamJam prev = getLastEndedTeamJam();
		int change = (Integer)value;
		if (flag != Flag.CHANGE) {
		    change -= getScore();
		}
		if (-change > getScore()) {
		    change = -getScore();
		}
		if (change == 0) {
		    return false;
		} else if (-change <= cur.getJamScore()) {
		    cur.changeJamScore(change);
		} else if (prev != null && prev != cur && -change <= cur.getJamScore() + prev.getJamScore()) {
		    requestBatchStart();
		    prev.changeJamScore(change + cur.getJamScore()); // change must be < 0 here
		    cur.setJamScore(0);
		    requestBatchEnd();
		} else {
		    cur.changeOsOffset(change);
		}
		return true;
	    }
	    if (prop == Value.LEAD_JAMMER) {
	        if ("false".equals(((String)value).toLowerCase())) {
	            value = Team.LEAD_NO_LEAD;
	        } else if ("true".equals(((String)value).toLowerCase())) {
	            value = Team.LEAD_LEAD;
	        }
	    }
	    Number min = (value instanceof Integer) ? 0 : null;
	    Number max = null;
	    if (prop == Value.TIMEOUTS) { max =  scoreBoard.getRulesets().getInt(Rule.NUMBER_TIMEOUTS); }
	    if (prop == Value.OFFICIAL_REVIEWS) { max = scoreBoard.getRulesets().getInt(Rule.NUMBER_REVIEWS); }
	    return super.set(prop, value, flag, min, max, 0);
	}
    }
    protected void valueChanged(PermanentProperty prop, Object value, Object last) {
	if (prop == Value.RETAINED_OFFICIAL_REVIEW && (Boolean)value && getOfficialReviews() == 0) {
	    setOfficialReviews(1);
	} else if (prop == Value.LEAD_JAMMER && Team.LEAD_LEAD.equals((String)value) &&
		scoreBoard.isInJam()) {
	    String otherId = getId().equals(Team.ID_1) ? Team.ID_2 : Team.ID_1;
	    Team otherTeam = getScoreBoard().getTeam(otherId);
	    if (Team.LEAD_LEAD.equals(otherTeam.getLeadJammer())) {
		otherTeam.setLeadJammer(Team.LEAD_NO_LEAD);
	    }
	} else if (prop == Value.STAR_PASS) {
            if (getPosition(FloorPosition.JAMMER).getSkater() != null) {
        	getPosition(FloorPosition.JAMMER).getSkater().setRole(FloorPosition.JAMMER.getRole(getRunningOrUpcomingTeamJam()));
            }
            if (getPosition(FloorPosition.PIVOT).getSkater() != null) {
        	getPosition(FloorPosition.PIVOT).getSkater().setRole(FloorPosition.PIVOT.getRole(getRunningOrUpcomingTeamJam()));
            }
            if ((Boolean)value && Team.LEAD_LEAD.equals(getLeadJammer())) {
        	setLeadJammer(Team.LEAD_LOST_LEAD);
            }
	}
    }
    
    public void execute(CommandProperty prop) {
	switch((Command)prop) {
	case OFFICIAL_REVIEW:
	    officialReview();
	    break;
	case TIMEOUT:
	    timeout();
	    break;
	}
    }
    
    public ValueWithId create(AddRemoveProperty prop, String id) {
	synchronized (coreLock) {
	    switch((Child)prop) {
	    case ALTERNATE_NAME:
		return new AlternateNameImpl(this, id, "");
	    case COLOR:
		return new ColorImpl(this, id, "");
	    case SKATER:
		return new SkaterImpl(this, id, "", "", "");
	    case POSITION:
		return null;
	    }
	    return null;
	}
    }
    
    public boolean remove(AddRemoveProperty prop, ValueWithId item) {
	synchronized (coreLock) {
	    boolean result = super.remove(prop, item);
	    if (result && prop == Child.SKATER) {
		((Skater)item).removeAll(Skater.Child.FIELDING);
		((Skater)item).removeAll(Skater.Child.PENALTY);
	    }
	    return result;
	}
    }
    
    public ScoreBoard getScoreBoard() { return scoreBoard; }

    public void reset() {
        synchronized (coreLock) {
            setName(DEFAULT_NAME_PREFIX + id);
            setLogo(DEFAULT_LOGO);
            updateTeamJams();
            resetTimeouts(true);

            removeAll(Child.ALTERNATE_NAME);
            removeAll(Child.COLOR);
            removeAll(Child.SKATER);
            for (ValueWithId p : getAll(Child.POSITION)) {
                ((Position)p).reset();
            }
        }
    }

    public String getId() { return id; }

    public String getName() { return (String)get(Value.NAME); }
    public void setName(String n) { set(Value.NAME, n); }

    public void startJam() {
        synchronized (coreLock) {
            updateTeamJams();
        }
    }

    public void stopJam() {
        synchronized (coreLock) {
            requestBatchStart();
            updateTeamJams();

            Map<Skater, Role> toField = new HashMap<Skater, Role>();
            TeamJam upcomingTJ = getRunningOrUpcomingTeamJam();
            TeamJam endedTJ = getRunningOrEndedTeamJam();
            for (FloorPosition fp : FloorPosition.values()) {
        	getPosition(fp).reset();
        	Skater s = endedTJ.getFielding(fp).getSkater();
        	if (s != null && endedTJ.getFielding(fp).getPenaltyBox()) {
        	    if (fp.getRole(endedTJ) != fp.getRole(upcomingTJ)) {
        		toField.put(s, fp.getRole(endedTJ));
        	    } else {
        		upcomingTJ.getFielding(fp).setSkater(s);
        	    }
        	} else if (s != null) {
        	    s.set(Skater.Value.CURRENT_FIELDING, null);
        	}
            }            
            nextReplacedBlocker = FloorPosition.PIVOT;
            for (Skater s : toField.keySet()) {
        	field(s, toField.get(s));
            }
            requestBatchEnd();
        }
    }

    public TeamSnapshot snapshot() {
        synchronized (coreLock) {
            return new TeamSnapshotImpl(this);
        }
    }
    public void restoreSnapshot(TeamSnapshot s) {
        synchronized (coreLock) {
            if (s.getId() != getId()) {	return; }
            setTimeouts(s.getTimeouts());
            setOfficialReviews(s.getOfficialReviews());
            setInTimeout(s.inTimeout());
            setInOfficialReview(s.inOfficialReview());
            for (ValueWithId skater : getAll(Child.SKATER)) {
                ((Skater)skater).restoreSnapshot(s.getSkaterSnapshot(skater.getId()));
            }
            updateTeamJams();
        }
    }

    public AlternateName getAlternateName(String i) { return (AlternateName)get(Child.ALTERNATE_NAME, i); }
    public void setAlternateName(String i, String n) {
        synchronized (coreLock) {
            requestBatchStart();
            ((AlternateName)get(Child.ALTERNATE_NAME, i, true)).setName(n);
            requestBatchEnd();
        }
    }
    public void removeAlternateName(String i) { remove(Child.ALTERNATE_NAME, getAlternateName(i)); }

    public Color getColor(String i) { return (Color)get(Child.COLOR, i); }
    public void setColor(String i, String c) {
        synchronized (coreLock) {
            requestBatchStart();
            ((Color)get(Child.COLOR, i, true)).setColor(c);
            requestBatchEnd();
        }
    }
    public void removeColor(String i) { remove(Child.COLOR, getColor(i)); }

    public String getLogo() { return (String)get(Value.LOGO); }
    public void setLogo(String l) { set(Value.LOGO, l); }

    public void timeout() {
        synchronized (coreLock) {
            if (getTimeouts() > 0) {
                getScoreBoard().setTimeoutType(this, false);
                changeTimeouts(-1);
            }
        }
    }
    public void officialReview() {
        synchronized (coreLock) {
            if (getOfficialReviews() > 0) {
                getScoreBoard().setTimeoutType(this, true);
                changeOfficialReviews(-1);
            }
        }
    }
    
    public TeamJam getRunningOrUpcomingTeamJam() { return (TeamJam)get(Value.RUNNING_OR_UPCOMING_TEAM_JAM); }
    public TeamJam getRunningOrEndedTeamJam() { return (TeamJam)get(Value.RUNNING_OR_ENDED_TEAM_JAM); }
    public TeamJam getLastEndedTeamJam() { return (TeamJam)get(Value.LAST_ENDED_TEAM_JAM); }
    public void updateTeamJams() {
	synchronized (coreLock) {
	    requestBatchStart();
	    set(Value.RUNNING_OR_ENDED_TEAM_JAM, scoreBoard.getCurrentPeriod().getCurrentJam().getTeamJam(id));
	    set(Value.RUNNING_OR_UPCOMING_TEAM_JAM, scoreBoard.isInJam() ? getRunningOrEndedTeamJam() : getRunningOrEndedTeamJam().getNext());
	    set(Value.LAST_ENDED_TEAM_JAM, getRunningOrUpcomingTeamJam().getPrevious());
	    requestBatchEnd();
	}
    }


    public int getScore() { return (Integer)get(Value.SCORE); }
    public void setScore(int s) { set(Value.SCORE, s); }
    public void changeScore(int c) { set(Value.SCORE, c, Flag.CHANGE); }

    public int getLastScore() { return (Integer)get(Value.LAST_SCORE); }
    public void setLastScore(int s) { set(Value.LAST_SCORE, s); }
    public void changeLastScore(int c) { set(Value.LAST_SCORE, c, Flag.CHANGE); }

    public boolean inTimeout() { return (Boolean)get(Value.IN_TIMEOUT); }
    public void setInTimeout(boolean b) { set(Value.IN_TIMEOUT, b); }

    public boolean inOfficialReview() { return (Boolean)get(Value.IN_OFFICIAL_REVIEW); }
    public void setInOfficialReview(boolean b) { set(Value.IN_OFFICIAL_REVIEW, b); }

    public boolean retainedOfficialReview() { return (Boolean)get(Value.RETAINED_OFFICIAL_REVIEW); }
    public void setRetainedOfficialReview(boolean b) { set(Value.RETAINED_OFFICIAL_REVIEW, b); }

    public int getTimeouts() { return (Integer)get(Value.TIMEOUTS); }
    public void setTimeouts(int t) { set(Value.TIMEOUTS, t); }
    public void changeTimeouts(int c) { set(Value.TIMEOUTS, c, Flag.CHANGE); } 
    public int getOfficialReviews() { return (Integer)get(Value.OFFICIAL_REVIEWS); }
    public void setOfficialReviews(int r) { set(Value.OFFICIAL_REVIEWS, r); }
    public void changeOfficialReviews(int c) { set(Value.OFFICIAL_REVIEWS, c, Flag.CHANGE); }
    public void resetTimeouts(boolean gameStart) {
        synchronized (coreLock) {
            setInTimeout(false);
            setInOfficialReview(false);
            if (gameStart || scoreBoard.getRulesets().getBoolean(Rule.TIMEOUTS_PER_PERIOD)) {
                setTimeouts(scoreBoard.getRulesets().getInt(Rule.NUMBER_TIMEOUTS));
            }
            if (gameStart || scoreBoard.getRulesets().getBoolean(Rule.REVIEWS_PER_PERIOD)) {
                setOfficialReviews(scoreBoard.getRulesets().getInt(Rule.NUMBER_REVIEWS));
                setRetainedOfficialReview(false);
            }
        }
    }

    public static Comparator<Skater> SkaterComparator = new Comparator<Skater>() {
        public int compare(Skater s1, Skater s2) {
            if (s2 == null) {
                return 1;
            }
            String n1 = s1.getNumber();
            String n2 = s2.getNumber();
            if (n1 == null) { return -1; }
            if (n2 == null) { return 1; }

            return n1.compareTo(n2);
        }
    };

    public Skater getSkater(String id) { return (Skater)get(Child.SKATER, id); }
    public Skater addSkater(String id) { return (Skater)get(Child.SKATER, id, true); }
    public Skater addSkater(String id, String n, String num, String flags) {
        synchronized (coreLock) {
            Skater s = new SkaterImpl(this, id, n, num, flags);
            addSkater(s);
            return s;
        }
    }
    public void addSkater(Skater skater) { add(Child.SKATER, skater); }
    public void removeSkater(String id) { remove(Child.SKATER, id); }

    public Position getPosition(FloorPosition fp) { return fp == null ? null : (Position)get(Child.POSITION, fp.toString()); }

    public void field(Skater s, Role r) {
	synchronized (coreLock) {
	    if (s == null) { return; }
	    requestBatchStart();
	    if (s.getPosition() == getPosition(FloorPosition.PIVOT)) {
		setNoPivot(r != Role.PIVOT);
		if (r == Role.BLOCKER || r == Role.PIVOT) {
		    s.setRole(r);
		}
	    }
	    if (s.getRole() != r) {
		Position p = getAvailablePosition(r);
		if (r == Role.PIVOT && p != null) {
		    if (p.getSkater() != null && (hasNoPivot() || s.getRole() == Role.BLOCKER)) {
			// If we are moving a blocker to pivot, move the previous pivot to blocker
			// If we are replacing a blocker from the pivot spot,
			//  see if we have a blocker spot available for them instead
			Position p2;
			if (s.getRole() == Role.BLOCKER) {
			    p2 = s.getPosition();
			} else {
			    p2 = getAvailablePosition(Role.BLOCKER);
			}
			p2.setSkater(p.getSkater());
		    }
		    setNoPivot(false);
		}
		if (p != null) { p.setSkater(s); }
		else { s.removeCurrentFielding(); }
	    }
	    requestBatchEnd();
	}
    }
    private Position getAvailablePosition(Role r) {
	switch (r) {
	case JAMMER:
	    if (isStarPass()) {
		return getPosition(FloorPosition.PIVOT);
	    } else {
		return getPosition(FloorPosition.JAMMER);
	    }
	case PIVOT:
	    if (isStarPass()) {
		return null;
	    } else {
		return getPosition(FloorPosition.PIVOT);
	    }
	case BLOCKER:
	    Position[] ps = {getPosition(FloorPosition.BLOCKER1),
		    getPosition(FloorPosition.BLOCKER2),
		    getPosition(FloorPosition.BLOCKER3)};
	    for (Position p : ps) {
		if (p.getSkater() == null) { 
		    return p; 
		}
	    }
	    Position fourth = getPosition(isStarPass() ? FloorPosition.JAMMER : FloorPosition.PIVOT);
	    if (fourth.getSkater() == null) {
		return fourth;
	    }
	    int tries = 0;
	    do {
		if (++tries > 4) { return null; }
		switch (nextReplacedBlocker) {
		case BLOCKER1:
		    nextReplacedBlocker = FloorPosition.BLOCKER2;
		    break;
		case BLOCKER2:
		    nextReplacedBlocker = FloorPosition.BLOCKER3;
		    break;
		case BLOCKER3:
		    nextReplacedBlocker = (hasNoPivot() && !isStarPass()) ? FloorPosition.PIVOT : FloorPosition.BLOCKER1;
		    break;
		case PIVOT:
		    nextReplacedBlocker = FloorPosition.BLOCKER1;
		    break;
		default:
		    break;
		}
	    } while(getPosition(nextReplacedBlocker).isPenaltyBox());
	    return getPosition(nextReplacedBlocker);
	default:
	    return null;
	}
    }
    
    public String getLeadJammer() { return (String)get(Value.LEAD_JAMMER); }
    public void setLeadJammer(String lead) { set(Value.LEAD_JAMMER, lead); }

    public boolean isStarPass() { return (Boolean)get(Value.STAR_PASS); }
    public void setStarPass(boolean sp) { set(Value.STAR_PASS, sp); }

    public boolean hasNoPivot() { return (Boolean)get(Value.NO_PIVOT); }
    private void setNoPivot(boolean noPivot) { set(Value.NO_PIVOT, noPivot); }

    protected String id;

    FloorPosition nextReplacedBlocker = FloorPosition.PIVOT;
    
    public static final String DEFAULT_NAME_PREFIX = "Team ";
    public static final String DEFAULT_LOGO = "";
    public static final int DEFAULT_SCORE = 0;
    public static final int DEFAULT_TIMEOUTS = 3;
    public static final int DEFAULT_OFFICIAL_REVIEWS = 1;
    public static final String DEFAULT_LEADJAMMER = Team.LEAD_NO_LEAD;
    public static final boolean DEFAULT_STARPASS = false;

    public class AlternateNameImpl extends ScoreBoardEventProviderImpl implements AlternateName {
        public AlternateNameImpl(Team t, String i, String n) {
            super(t, Team.Child.ALTERNATE_NAME, AlternateName.class, Value.class);
            team = t;
            id = i;
            setName(n);
        }
        public String getId() { return id; }
        public String getName() { return (String)get(Value.NAME); }
        public void setName(String n) { set(Value.NAME, n); }

        public Team getTeam() { return team; }

        protected Team team;
        protected String id;
    }

    public class ColorImpl extends ScoreBoardEventProviderImpl implements Color {
        public ColorImpl(Team t, String i, String c) {
            super(t, Team.Child.COLOR, Color.class, Value.class);
            team = t;
            id = i;
            setColor(c);
        }
        public String getId() { return id; }
        public String getColor() { return (String)get(Value.COLOR); }
        public void setColor(String c) { set(Value.COLOR, c); }

        public Team getTeam() { return team; }

        protected Team team;
        protected String id;
        protected String color;
    }

    public static class TeamSnapshotImpl implements TeamSnapshot {
        private TeamSnapshotImpl(Team team) {
            id = team.getId();
            timeouts = team.getTimeouts();
            officialReviews = team.getOfficialReviews();
            inTimeout = team.inTimeout();
            inOfficialReview = team.inOfficialReview();
            skaterSnapshots = new HashMap<String, Skater.SkaterSnapshot>();
            for (ValueWithId skater : team.getAll(Child.SKATER)) {
                skaterSnapshots.put(skater.getId(), ((Skater)skater).snapshot());
            }
        }

        public String getId() { return id; }
        public int getTimeouts() { return timeouts; }
        public int getOfficialReviews() { return officialReviews; }
        public boolean inTimeout() { return inTimeout; }
        public boolean inOfficialReview() { return inOfficialReview; }
        public Map<String, Skater.SkaterSnapshot> getSkaterSnapshots() { return skaterSnapshots; }
        public Skater.SkaterSnapshot getSkaterSnapshot(String skater) { return skaterSnapshots.get(skater); }

        protected String id;
        protected int timeouts;
        protected int officialReviews;
        protected boolean inTimeout;
        protected boolean inOfficialReview;
        protected Map<String, Skater.SkaterSnapshot> skaterSnapshots;
    }
}
