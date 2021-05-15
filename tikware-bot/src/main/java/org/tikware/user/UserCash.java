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

public class UserCash {
    private String id;
    private Double cash;
    private Character source;
    private String tradingDay;
    private String time;

    /**
     * Cash is from deposit, positive amount.
     */
    public static final Character DEPOSIT = '0';

    /**
     * Cash is for withdraw, negative amount.
     */
    public static final Character WITHDRAW = '1';

    /**
     * Cash is from closing profit, positive or negative amount.
     */
    public static final Character CLOSE = '2';


    public void setId(String id) {
        this.id = id;
    }

    public void setCash(Double cash) {
        this.cash = cash;
    }

    public void setSource(Character source) {
        this.source = source;
    }

    public void setTradingDay(String tradingDay) {
        this.tradingDay = tradingDay;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getId() {
        return id;
    }

    public Double getCash() {
        return cash;
    }

    /**
     * Cash source defines where the cash comes from.
     * @return cash source.
     */
    public Character getSource() {
        return source;
    }

    public String getTradingDay() {
        return tradingDay;
    }

    public String getTime() {
        return time;
    }
}
