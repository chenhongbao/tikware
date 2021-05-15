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

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Tick {
    private String id;
    private String symbol;
    private Double askPrice;
    private Double bidPrice;
    private Long askVolume;
    private Long bidVolume;
    private Double dayHighPrice;
    private Double dayLowPrice;
    private Double highLimitPrice;
    private Double lowLimitPrice;
    private Double preSettlementPrice;
    private Double preClosePrice;
    private Double settlementPrice;
    private Double closePrice;
    private Double averagePrice;
    private Double price;
    private Long dayVolume;
    private Long dayPosition;
    private LocalDate tradingDay;
    private LocalDateTime time;

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

    public Double getAskPrice() {
        return askPrice;
    }

    public void setAskPrice(Double askPrice) {
        this.askPrice = askPrice;
    }

    public Double getBidPrice() {
        return bidPrice;
    }

    public void setBidPrice(Double bidPrice) {
        this.bidPrice = bidPrice;
    }

    public Long getAskVolume() {
        return askVolume;
    }

    public void setAskVolume(Long askVolume) {
        this.askVolume = askVolume;
    }

    public Long getBidVolume() {
        return bidVolume;
    }

    public void setBidVolume(Long bidVolume) {
        this.bidVolume = bidVolume;
    }

    public Double getDayHighPrice() {
        return dayHighPrice;
    }

    public void setDayHighPrice(Double dayHighPrice) {
        this.dayHighPrice = dayHighPrice;
    }

    public Double getDayLowPrice() {
        return dayLowPrice;
    }

    public void setDayLowPrice(Double dayLowPrice) {
        this.dayLowPrice = dayLowPrice;
    }

    public Double getHighLimitPrice() {
        return highLimitPrice;
    }

    public void setHighLimitPrice(Double highLimitPrice) {
        this.highLimitPrice = highLimitPrice;
    }

    public Double getLowLimitPrice() {
        return lowLimitPrice;
    }

    public void setLowLimitPrice(Double lowLimitPrice) {
        this.lowLimitPrice = lowLimitPrice;
    }

    public Double getPreSettlementPrice() {
        return preSettlementPrice;
    }

    public void setPreSettlementPrice(Double preSettlementPrice) {
        this.preSettlementPrice = preSettlementPrice;
    }

    public Double getPreClosePrice() {
        return preClosePrice;
    }

    public void setPreClosePrice(Double preClosePrice) {
        this.preClosePrice = preClosePrice;
    }

    public Double getSettlementPrice() {
        return settlementPrice;
    }

    public void setSettlementPrice(Double settlementPrice) {
        this.settlementPrice = settlementPrice;
    }

    public Double getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(Double closePrice) {
        this.closePrice = closePrice;
    }

    public Double getAveragePrice() {
        return averagePrice;
    }

    public void setAveragePrice(Double averagePrice) {
        this.averagePrice = averagePrice;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Long getDayVolume() {
        return dayVolume;
    }

    public void setDayVolume(Long dayVolume) {
        this.dayVolume = dayVolume;
    }

    public Long getDayPosition() {
        return dayPosition;
    }

    public void setDayPosition(Long dayPosition) {
        this.dayPosition = dayPosition;
    }

    public LocalDate getTradingDay() {
        return tradingDay;
    }

    public void setTradingDay(LocalDate tradingDay) {
        this.tradingDay = tradingDay;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }
}
