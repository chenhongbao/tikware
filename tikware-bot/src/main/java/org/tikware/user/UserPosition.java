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

public class UserPosition {
    private String id;
    private String user;
    private String symbol;
    private String exchange;
    private Double price;
    private Long multiple;
    private Double margin;
    private Character direction;
    private String openTradingDay;
    private String openTime;
    private Character state;

    /**
     * Long position.
     */
    public static final Character LONG = 'a';

    /**
     * Short position.
     */
    public static final Character SHORT = 'b';

    /**
     * The position is frozen for opening.
     */
    public static final Character FROZEN_OPEN = 'A';

    /**
     * The position is frozen for closing.
     */
    public static final Character FROZEN_CLOSE = 'B';

    /**
     * The position is normal.
     */
    public static final Character NORMAL = 'C';

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Long getMultiple() {
        return multiple;
    }

    public void setMultiple(Long multiple) {
        this.multiple = multiple;
    }

    public Double getMargin() {
        return margin;
    }

    public void setMargin(Double margin) {
        this.margin = margin;
    }

    public Character getDirection() {
        return direction;
    }

    public void setDirection(Character direction) {
        this.direction = direction;
    }

    public String getOpenTradingDay() {
        return openTradingDay;
    }

    public void setOpenTradingDay(String openTradingDay) {
        this.openTradingDay = openTradingDay;
    }

    public String getOpenTime() {
        return openTime;
    }

    public void setOpenTime(String openTime) {
        this.openTime = openTime;
    }

    public Character getState() {
        return state;
    }

    public void setState(Character state) {
        this.state = state;
    }
}
