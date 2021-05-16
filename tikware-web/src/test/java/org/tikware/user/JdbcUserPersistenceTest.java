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

import static org.junit.jupiter.api.Assertions.*;

public class JdbcUserPersistenceTest {

    @Test
    public void getTradingDay() {
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
}