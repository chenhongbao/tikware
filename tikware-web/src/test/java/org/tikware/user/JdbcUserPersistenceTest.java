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


import org.junit.jupiter.api.Test;
import org.tikware.api.Order;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JdbcUserPersistenceTest {

    @Test
    public void getTradingDay() {
        JdbcUserPersistence.clearDb();
        var x = "20210518";
        var c = new JdbcUserPersistence();
        // First query empty table.
        var day = c.getTradingDay();
        if (day.isBlank() || !c.equals(x)) {
            // Add trading day.
            c.addTradingDay(x);
            day = c.getTradingDay();
        }
        // Check equality.
        assertEquals(day, x);
    }

    @Test
    void getPrice() {
        JdbcUserPersistence.clearDb();
        var p = 1335.58D;
        var i = "c2109";
        var c = new JdbcUserPersistence();
        // First query empty table.
        var price = c.getPrice(i);
        if (price.isNaN() || price != p) {
            // Add or update price.
            c.addOrUpdatePrice(i, p);
            price = c.getPrice(i);
        }
        // Check equality.
        assertEquals(price, p);
    }

    @Test
    void getMultiple() {
        JdbcUserPersistence.clearDb();
        var m = 10L;
        var i = "c2111";
        var c = new JdbcUserPersistence();
        // First query empty table.
        var multi = c.getMultiple(i);
        if (multi == null || multi != m) {
            // Add or update multiple.
            c.addOrUpdateMultiple(i, m);
            multi = c.getMultiple(i);
        }
        // Check equality.
        assertEquals(m, multi);
    }

    @Test
    void getMargin() {
        JdbcUserPersistence.clearDb();
        var symbol = "c2111";
        var ratio = .12D;
        var c = new JdbcUserPersistence();
        var margin = c.getMargin(symbol, 1234.0D, UserPosition.LONG, Order.OPEN);
        if (margin.isNaN()) {
            // Add margin.
            c.addOrUpdateMultiple(symbol, 10L);
            c.addOrUpdateMarginRatio(symbol, ratio, UserPosition.LONG, Order.OPEN,
                    UserPersistence.RATIO_BY_AMOUNT);
            margin = c.getMargin(symbol, 1234.0D, UserPosition.LONG, Order.OPEN);
        }
        // Check equality.
        assertEquals(margin, 1234.0D * 10 * ratio);
    }

    @Test
    void getCommission() {
        JdbcUserPersistence.clearDb();
        var symbol = "c2111";
        var ratio = 1.2D;
        var c = new JdbcUserPersistence();
        var commission = c.getCommission(symbol, 1234.0D, UserPosition.SHORT, Order.CLOSE);
        if (commission.isNaN()) {
            // Add margin.
            c.addOrUpdateMultiple(symbol, 10L);
            c.addOrUpdateCommissionRatio(symbol, ratio, UserPosition.SHORT, Order.CLOSE,
                    UserPersistence.RATIO_BY_VOLUME);
            commission = c.getCommission(symbol, 1234.0D, UserPosition.SHORT, Order.CLOSE);
        }
        // Check equality.
        assertEquals(commission, ratio);
    }
}