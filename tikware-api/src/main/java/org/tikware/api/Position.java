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

package org.tikware.api;

public class Position {
    private String symbol;
    private Character direction;
    private Long volume;
    private Double margin;
    private Long closingVolume;
    private Double closingMargin;
    private Long openingVolume;
    private Double openingMargin;
    private Double positionProfit;
    private String tradingDay;
    private String time;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Character getDirection() {
        return direction;
    }

    public void setDirection(Character direction) {
        this.direction = direction;
    }

    public Long getVolume() {
        return volume;
    }

    public void setVolume(Long volume) {
        this.volume = volume;
    }

    public Double getMargin() {
        return margin;
    }

    public void setMargin(Double margin) {
        this.margin = margin;
    }

    public Long getClosingVolume() {
        return closingVolume;
    }

    public void setClosingVolume(Long closingVolume) {
        this.closingVolume = closingVolume;
    }

    public Double getClosingMargin() {
        return closingMargin;
    }

    public void setClosingMargin(Double closingMargin) {
        this.closingMargin = closingMargin;
    }

    public Long getOpeningVolume() {
        return openingVolume;
    }

    public void setOpeningVolume(Long openingVolume) {
        this.openingVolume = openingVolume;
    }

    public Double getOpeningMargin() {
        return openingMargin;
    }

    public void setOpeningMargin(Double openingMargin) {
        this.openingMargin = openingMargin;
    }

    public Double getPositionProfit() {
        return positionProfit;
    }

    public void setPositionProfit(Double positionProfit) {
        this.positionProfit = positionProfit;
    }

    public String getTradingDay() {
        return tradingDay;
    }

    public void setTradingDay(String tradingDay) {
        this.tradingDay = tradingDay;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }
}
