/*
 * Copyright (c) 2020-2021. Hongbao Chen <chenhongbao@outlook.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.tikware.user;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tikware.api.Order;
import org.tikware.api.Trade;

import static org.junit.jupiter.api.Assertions.*;

public class JdbcUserPersistenceTest {

    private final String dir = "~/tikware/database";
    private final String db = "h2";
    private H2Persistence persistence;

    @BeforeEach
    public void setup() {
        persistence = new H2Persistence(dir, db);
    }

    @AfterEach
    public void clear() {
        if (persistence != null) {
            persistence.deleteDb();
        }
    }

    protected JdbcUserPersistence db() {
        return persistence;
    }

    @Test
    public void getTradingDay() {
        var x = "20210518";
        // First query empty table.
        var day = db().getTradingDay();
        if (day.isBlank() || !db().equals(x)) {
            // Add trading day.
            db().addTradingDay(x);
            day = db().getTradingDay();
        }
        // Check equality.
        assertEquals(day, x);
    }

    @Test
    void getPrice() {
        var p = 1335.58D;
        var i = "c2109";
        // First query empty table.
        var price = db().getPrice(i);
        if (price.isNaN() || price != p) {
            // Add or update price.
            db().addOrUpdatePrice(i, p);
            price = db().getPrice(i);
        }
        // Check equality.
        assertEquals(price, p);
    }

    @Test
    void getMultiple() {
        var m = 10L;
        var i = "c2111";
        // First query empty table.
        var multi = db().getMultiple(i);
        if (multi == null || multi != m) {
            // Add or update multiple.
            db().addOrUpdateMultiple(i, m);
            multi = db().getMultiple(i);
        }
        // Check equality.
        assertEquals(m, multi);
    }

    @Test
    void getMargin() {
        var symbol = "c2111";
        var ratio = .12D;
        var margin = db().getMargin(symbol, 1234.0D, UserPosition.LONG, Order.OPEN);
        if (margin.isNaN()) {
            // Add margin.
            db().addOrUpdateMultiple(symbol, 10L);
            db().addOrUpdateMarginRatio(symbol, ratio, UserPosition.LONG, Order.OPEN,
                    UserPersistence.RATIO_BY_AMOUNT);
            margin = db().getMargin(symbol, 1234.0D, UserPosition.LONG, Order.OPEN);
        }
        // Check equality.
        assertEquals(margin, 1234.0D * 10 * ratio);
    }

    @Test
    void getCommission() {
        var symbol = "c2111";
        var ratio = 1.2D;
        var commission = db().getCommission(symbol, 1234.0D, UserPosition.SHORT, Order.CLOSE);
        if (commission.isNaN()) {
            // Add margin.
            db().addOrUpdateMultiple(symbol, 10L);
            db().addOrUpdateCommissionRatio(symbol, ratio, UserPosition.SHORT, Order.CLOSE,
                    UserPersistence.RATIO_BY_VOLUME);
            commission = db().getCommission(symbol, 1234.0D, UserPosition.SHORT, Order.CLOSE);
        }
        // Check equality.
        assertEquals(commission, ratio);
    }

    @Test
    void getUserBalance() {
        var b = new UserBalance();
        b.setId("U001");
        b.setUser("hb.chen");
        b.setBalance(120983202.0D);
        b.setTradingDay("20210517");
        b.setTime("20210517 10:32:02.465");
        // First query and get null.
        // Test add.
        var ub = db().getUserBalance("hb.chen");
        if (ub == null) {
            db().alterUserBalance("hb.chen", b, UserPersistence.ALTER_ADD);
            ub = db().getUserBalance("hb.chen");
            // Mustn't be null.
            assertNotNull(ub);
        }
        // Check equality.
        assertEquals(b.getId(), ub.getId());
        assertEquals(b.getUser(), ub.getUser());
        assertEquals(b.getBalance(), ub.getBalance());
        assertEquals(b.getTradingDay(), ub.getTradingDay());
        assertEquals(b.getTime(), ub.getTime());
        // Test update.
        var newBlc = b.getBalance() * 2;
        b.setBalance(newBlc);
        db().alterUserBalance("hb.chen", b, UserPersistence.ALTER_UPDATE);
        ub = db().getUserBalance("hb.chen");
        // Check update works.
        assertEquals(b.getBalance(), newBlc);
        // Check other fields same.
        assertEquals(b.getId(), ub.getId());
        assertEquals(b.getUser(), ub.getUser());
        assertEquals(b.getTradingDay(), ub.getTradingDay());
        assertEquals(b.getTime(), ub.getTime());
        // Test delete,
        db().alterUserBalance("hb.chen", ub, UserPersistence.ALTER_DELETE);
        ub = db().getUserBalance("hb.chen");
        // Check query returns null.
        assertNull(ub);
    }

    @Test
    public void getUserPosition() {
        var p = new UserPosition();
        p.setId("P-10000001");
        p.setUser("hb.chen");
        p.setSymbol("c2109");
        p.setExchange("DCE");
        p.setPrice(3281.3);
        p.setMultiple(10L);
        p.setMargin(3587.8D);
        p.setDirection(UserPosition.SHORT);
        p.setOpenTradingDay("20210517");
        p.setOpenTime("20210517 14:23:09.387");
        p.setState(UserPosition.FROZEN_OPEN);
        var up = db().getUserPositions("hb.chen");
        if (up.isEmpty()) {
            db().alterUserPosition("hb.chen", p, UserPersistence.ALTER_ADD);
            up = db().getUserPositions("hb.chen");
            // Check adding result.
            assertNotNull(up);
            assertEquals(up.size(), 1);
        }
        // Get the only element.
        var p0 = up.iterator().next();
        // Check equality.
        assertEquals(p.getId(), p0.getId());
        assertEquals(p.getUser(), p0.getUser());
        assertEquals(p.getSymbol(), p0.getSymbol());
        assertEquals(p.getExchange(), p0.getExchange());
        assertEquals(p.getPrice(), p0.getPrice());
        assertEquals(p.getMultiple(), p0.getMultiple());
        assertEquals(p.getMargin(), p0.getMargin());
        assertEquals(p.getDirection(), p0.getDirection());
        assertEquals(p.getOpenTradingDay(), p0.getOpenTradingDay());
        assertEquals(p.getOpenTime(), p0.getOpenTime());
        assertEquals(p.getState(), p0.getState());
        // Test update.
        p0.setState(UserPosition.NORMAL);
        db().alterUserPosition("hb.chen", p0, UserPersistence.ALTER_UPDATE);
        up = db().getUserPositions("hb.chen");
        // Check update results.
        assertNotNull(up);
        assertEquals(up.size(), 1);
        // Check update field.
        var p1 = up.iterator().next();
        assertEquals(p1.getState(), p0.getState());
        // Check other fields.
        assertEquals(p1.getId(), p0.getId());
        assertEquals(p1.getUser(), p0.getUser());
        assertEquals(p1.getSymbol(), p0.getSymbol());
        assertEquals(p1.getExchange(), p0.getExchange());
        assertEquals(p1.getPrice(), p0.getPrice());
        assertEquals(p1.getMultiple(), p0.getMultiple());
        assertEquals(p1.getMargin(), p0.getMargin());
        assertEquals(p1.getDirection(), p0.getDirection());
        assertEquals(p1.getOpenTradingDay(), p0.getOpenTradingDay());
        assertEquals(p1.getOpenTime(), p0.getOpenTime());
        // Test more elements.
        p1.setId("P-10000002");
        db().alterUserPosition("hb.chen", p1, UserPersistence.ALTER_ADD);
        p1.setId("P-10000003");
        p1.setPrice(2461.0D);
        p1.setDirection(UserPosition.LONG);
        db().alterUserPosition("hb.chen", p1, UserPersistence.ALTER_ADD);
        // Check more results.
        up = db().getUserPositions("hb.chen");
        assertNotNull(up);
        assertEquals(3, up.size());
        // Test delete.
        // Delete first two elements.
        p.setId("P-10000001");
        db().alterUserPosition("hb.chen", p, UserPersistence.ALTER_DELETE);
        p.setId("P-10000002");
        db().alterUserPosition("hb.chen", p, UserPersistence.ALTER_DELETE);
        // There is only one element left.
        up = db().getUserPositions("hb.chen");
        // Check delete result.
        assertNotNull(up);
        assertEquals(1, up.size());
        // Check the queried element has the same fields with the last element.
        var p2 = up.iterator().next();
        assertEquals(p1.getId(), p2.getId());
        assertEquals(p1.getUser(), p2.getUser());
        assertEquals(p1.getSymbol(), p2.getSymbol());
        assertEquals(p1.getExchange(), p2.getExchange());
        assertEquals(p1.getPrice(), p2.getPrice());
        assertEquals(p1.getMultiple(), p2.getMultiple());
        assertEquals(p1.getMargin(), p2.getMargin());
        assertEquals(p1.getDirection(), p2.getDirection());
        assertEquals(p1.getOpenTradingDay(), p2.getOpenTradingDay());
        assertEquals(p1.getOpenTime(), p2.getOpenTime());
        assertEquals(p1.getState(), p2.getState());
    }

    @Test
    public void getUserCash() {
        var c = new UserCash();
        c.setId("C-10000001");
        c.setUser("hb.chen");
        c.setCash(3682.0);
        c.setSource(UserCash.CLOSE);
        c.setTradingDay("20210519");
        c.setTime("20210519 13:46:38.387");
        // Query empty table.
        var cs = db().getUserCashes("hb.chen");
        assertNotNull(cs);
        if (cs.isEmpty()) {
            db().alterUserCash("hb.chen", c, UserPersistence.ALTER_ADD);
            cs = db().getUserCashes("hb.chen");
            // Check add result.
            assertNotNull(cs);
            assertEquals(1, cs.size());
        }
        var c0 = cs.iterator().next();
        // Check equality.
        assertEquals(c.getId(), c0.getId());
        assertEquals(c.getUser(), c0.getUser());
        assertEquals(c.getCash(), c0.getCash());
        assertEquals(c.getSource(), c0.getSource());
        assertEquals(c.getTradingDay(), c0.getTradingDay());
        assertEquals(c.getTime(), c0.getTime());
        // Test update.
        var nt = "20210519 14:56:48.487";
        c0.setTime(nt);
        db().alterUserCash("hb.chen", c0, UserPersistence.ALTER_UPDATE);
        // Check update result.
        cs = db().getUserCashes("hb.chen");
        assertEquals(1, cs.size());
        var c1 = cs.iterator().next();
        // Check updated equality.
        assertEquals(c1.getId(), c0.getId());
        assertEquals(c1.getUser(), c0.getUser());
        assertEquals(c1.getCash(), c0.getCash());
        assertEquals(c1.getSource(), c0.getSource());
        assertEquals(c1.getTradingDay(), c0.getTradingDay());
        assertEquals(c1.getTime(), c0.getTime());
        // Test more records.
        c1.setId("C-10000002");
        db().alterUserCash("hb.chen", c1, UserPersistence.ALTER_ADD);
        c1.setId("C-10000003");
        db().alterUserCash("hb.chen", c1, UserPersistence.ALTER_ADD);
        c1.setId("C-10000004");
        db().alterUserCash("hb.chen", c1, UserPersistence.ALTER_ADD);
        // Check add more rows.
        cs = db().getUserCashes("hb.chen");
        assertEquals(4, cs.size());
        // Test delete.
        c1.setId("C-10000001");
        db().alterUserCash("hb.chen", c1, UserPersistence.ALTER_DELETE);
        c1.setId("C-10000002");
        db().alterUserCash("hb.chen", c1, UserPersistence.ALTER_DELETE);
        c1.setId("C-10000003");
        db().alterUserCash("hb.chen", c1, UserPersistence.ALTER_DELETE);
        c1.setId("C-10000004");
        db().alterUserCash("hb.chen", c1, UserPersistence.ALTER_DELETE);
        // Check delete results.
        cs = db().getUserCashes("hb.chen");
        assertTrue(cs.isEmpty());
    }

    @Test
    public void getUserCommission() {
        var c = new UserCommission();
        c.setId("M-100000001");
        c.setUser("hb.chen");
        c.setSymbol("c2109");
        c.setDirection(UserPosition.LONG);
        c.setOffset(Order.OPEN);
        c.setCommission(1.21D);
        c.setTradingDay("20210517");
        c.setTime("20210517 17:15:34.231");
        c.setState(UserCommission.FROZEN);
        // Query empty table.
        var uc = db().getUserCommissions("hb.chen");
        assertTrue(uc.isEmpty());
        // Test add commission.
        db().alterUserCommission("hb.chen", c, UserPersistence.ALTER_ADD);
        // Check add result.
        uc = db().getUserCommissions("hb.chen");
        assertNotNull(uc);
        assertEquals(1, uc.size());
        // Check equality.
        var c0 = uc.iterator().next();
        assertEquals(c.getId(), c0.getId());
        assertEquals(c.getUser(), c0.getUser());
        assertEquals(c.getSymbol(), c0.getSymbol());
        assertEquals(c.getDirection(), c0.getDirection());
        assertEquals(c.getOffset(), c0.getOffset());
        assertEquals(c.getCommission(), c0.getCommission());
        assertEquals(c.getTradingDay(), c0.getTradingDay());
        assertEquals(c.getTime(), c0.getTime());
        assertEquals(c.getState(), c0.getState());
        // Test update commission.
        c0.setState(UserCommission.NORMAL);
        db().alterUserCommission("hb.chen", c0, UserPersistence.ALTER_UPDATE);
        // Check update result.
        uc = db().getUserCommissions("hb.chen");
        assertEquals(1, uc.size());
        var c1 = uc.iterator().next();
        assertEquals(c1.getId(), c0.getId());
        assertEquals(c1.getUser(), c0.getUser());
        assertEquals(c1.getSymbol(), c0.getSymbol());
        assertEquals(c1.getDirection(), c0.getDirection());
        assertEquals(c1.getOffset(), c0.getOffset());
        assertEquals(c1.getCommission(), c0.getCommission());
        assertEquals(c1.getTradingDay(), c0.getTradingDay());
        assertEquals(c1.getTime(), c0.getTime());
        assertEquals(c1.getState(), c0.getState());
        // Add more commissions.
        c1.setId("M-100000002");
        db().alterUserCommission("hb.chen", c1, UserPersistence.ALTER_ADD);
        c1.setId("M-100000003");
        c1.setCommission(0.0D);
        db().alterUserCommission("hb.chen", c1, UserPersistence.ALTER_ADD);
        // Check more results.
        uc = db().getUserCommissions("hb.chen");
        assertEquals(3, uc.size());
        // Test delete.
        c1.setId("M-100000001");
        db().alterUserCommission("hb.chen", c1, UserPersistence.ALTER_DELETE);
        c1.setId("M-100000002");
        db().alterUserCommission("hb.chen", c1, UserPersistence.ALTER_DELETE);
        // Check delete results/
        uc = db().getUserCommissions("hb.chen");
        assertEquals(1, uc.size());
        // Check equality.
        var c2 = uc.iterator().next();
        // Recover ID.
        c1.setId("M-100000003");
        assertEquals(c1.getId(), c2.getId());
        assertEquals(c1.getUser(), c2.getUser());
        assertEquals(c1.getSymbol(), c2.getSymbol());
        assertEquals(c1.getDirection(), c2.getDirection());
        assertEquals(c1.getOffset(), c2.getOffset());
        assertEquals(c1.getCommission(), c2.getCommission());
        assertEquals(c1.getTradingDay(), c2.getTradingDay());
        assertEquals(c1.getTime(), c2.getTime());
        assertEquals(c1.getState(), c2.getState());
    }

    @Test
    public void getUserInfo() {
        var u = new UserInfo();
        u.setId("U-1");
        u.setUser("hb.chen");
        u.setPassword("123456");
        u.setNickname("陈宏葆");
        u.setPrivilege(UserInfo.ADMIN);
        u.setJoinTime("20210517 17:52:23.324");
        // Query empty table.
        var us = db().getUserInfos();
        assertTrue(us.isEmpty());
        // Add one user.
        db().alterUserInfo(u, UserPersistence.ALTER_ADD);
        // Check add result.
        us = db().getUserInfos();
        assertEquals(1, us.size());
        // Check equality.
        var u0 = us.iterator().next();
        assertEquals(u.getId(), u0.getId());
        assertEquals(u.getUser(), u0.getUser());
        assertEquals(u.getPassword(), u0.getPassword());
        assertEquals(u.getNickname(), u0.getNickname());
        assertEquals(u.getPrivilege(), u0.getPrivilege());
        assertEquals(u.getJoinTime(), u0.getJoinTime());
        // Update user privilege.
        u0.setPrivilege(UserInfo.MANAGER);
        db().alterUserInfo(u0, UserPersistence.ALTER_UPDATE);
        // Check update result.
        us = db().getUserInfos();
        assertEquals(1, us.size());
        var u1 = us.iterator().next();
        assertEquals(u1.getId(), u0.getId());
        assertEquals(u1.getUser(), u0.getUser());
        assertEquals(u1.getPassword(), u0.getPassword());
        assertEquals(u1.getNickname(), u0.getNickname());
        assertEquals(u1.getPrivilege(), u0.getPrivilege());
        assertEquals(u1.getJoinTime(), u0.getJoinTime());
        // Test delete.
        db().alterUserInfo(u1, UserPersistence.ALTER_DELETE);
        // Check delete result.
        us = db().getUserInfos();
        assertTrue(us.isEmpty());
    }

    @Test
    public void getTrade() {
        var t = new Trade();
        t.setId("T-10000001");
        t.setUser("hb.chen");
        t.setOrderId("O-1111110");
        t.setSymbol("c2109");
        t.setExchange("DCE");
        t.setPrice(2650.0D);
        t.setQuantity(1L);
        t.setDirection(Order.BUY);
        t.setOffset(Order.OPEN);
        t.setTradingDay("20210531");
        t.setTime("20210531 14:56:54 653");
        // Query empty table.
        var ts = db().getTrades("hb.chen");
        assertNotNull(ts);
        // Add trade.
        if (ts.isEmpty()) {
            db().addTrade("hb.chen", t);
            ts = db().getTrades("hb.chen");
            assertNotNull(ts);
            assertTrue(!ts.isEmpty());
        }
        // Check fields.
        var t0 = ts.iterator().next();
        assertEquals(t.getId(), t0.getId());
        assertEquals(t.getUser(), t0.getUser());
        assertEquals(t.getOrderId(), t0.getOrderId());
        assertEquals(t.getSymbol(), t0.getSymbol());
        assertEquals(t.getExchange(), t0.getExchange());
        assertEquals(t.getPrice(), t0.getPrice());
        assertEquals(t.getQuantity(), t0.getQuantity());
        assertEquals(t.getDirection(), t0.getDirection());
        assertEquals(t.getOffset(), t0.getOffset());
        assertEquals(t.getTradingDay(), t0.getTradingDay());
        assertEquals(t.getTime(), t0.getTime());
    }
}