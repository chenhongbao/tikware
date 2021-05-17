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
}