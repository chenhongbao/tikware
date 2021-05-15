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

import org.tikware.api.Order;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class User {
    private final UserBalance balance = new UserBalance();
    private final Map<String, UserPosition> positions = new ConcurrentHashMap<>();
    private final Map<String, UserCommission> commissions = new ConcurrentHashMap<>();
    private final Collection<UserCash> cashes = new ConcurrentLinkedQueue<>();
    private final UserCommon common;

    public User(UserBalance balance, Collection<UserPosition> positions,
            Collection<UserCommission> commissions, Collection<UserCash> cashes,
            UserCommon userCommon) {
        this.common = userCommon;
        this.cashes.addAll(cashes);
        copyBalance(this.balance, balance);
        copyCommissions(this.commissions, commissions);
        copyPositions(this.positions, positions);
    }

    public UserCommon getCommon() {
        return common;
    }

    public User settle() {
        clearFrozen();
        var b = new UserBalance();
        b.setId(balance.getId());
        b.setUser(balance.getUser());
        b.setBalance(getDynamicBalance());
        b.setTradingDay(common.getTradingDay());
        b.setTime(common.getDateTime());
        return new User(b, positions.values(), commissions.values(), cashes, common);
    }

    private void clearFrozen() {
        // Remove frozen, not traded commissions.
        commissions.values().removeIf(commission -> commission.getState() == UserCommission.FROZEN);
        // Remove frozen, not open positions.
        positions.values().removeIf(position -> position.getState() == UserPosition.FROZEN_OPEN);
        // Set frozen, not closed position to normal.
        positions.values().stream()
                 .filter(position -> position.getState() == UserPosition.FROZEN_CLOSE)
                 .forEach(position -> position.setState(UserPosition.NORMAL));
    }

    public void undo(OpenInfo info) {
        removePosition(info.getPositionId());
        removeCommission(info.getCommissionId());
    }

    private void removePosition(String positionId) {
        var r = positions.values().removeIf(position -> position.getId().equals(positionId));
        if (!r) {
            throw new PositionNotFoundException("Fail undo open position: " + positionId + ".");
        }
    }

    public void undo(CloseInfo info) {
        var p = position(info.getPositionId());
        p.setState(UserPosition.NORMAL);
        removeCommission(info.getCommissionId());
    }

    private void removeCommission(String commissionId) {
        var r = commissions.values().removeIf(commission -> commission.getId().equals(commissionId));
        if (!r) {
            throw new CommissionNotFoundException("Fail undo open commission: " + commissionId + ".");
        }
    }

    private void copyCommissions(Map<String, UserCommission> to, Collection<UserCommission> from) {
        if (from == null || from.isEmpty()) {
            return;
        }
        from.forEach(commission -> to.put(commission.getId(), commission));
    }

    private void copyBalance(UserBalance to, UserBalance from) {
        to.setId(from.getId());
        to.setUser(from.getUser());
        to.setBalance(from.getBalance());
        to.setTradingDay(common.getTradingDay());
        to.setTime(common.getDateTime());
    }

    private void copyPositions(Map<String, UserPosition> to, Collection<UserPosition> from) {
        if (from == null || from.isEmpty()) {
            return;
        }
        from.forEach(position -> to.put(position.getId(), position));
    }

    public CloseInfo freezeClose(String symbol, Character direction, Double price)
            throws IllegalCommissionException {
        var commission = common.getCommission(symbol, price, direction, Order.CLOSE);
        checkCommission(commission);
        var info = findPosition(symbol, direction);
        if (info.getError() != null) {
            return info;
        } else {
            var commissionId = addCommission(symbol, direction, Order.CLOSE, commission);
            info.setCommissionId(commissionId);
            return info;
        }
    }

    private CloseInfo findPosition(String symbol, Character direction) {
        var info = new CloseInfo();
        List<UserPosition> p = positions
                .values().stream()
                .filter(position -> position.getState() == UserPosition.NORMAL &&
                                    position.getDirection() == direction)
                .collect(Collectors.toCollection(LinkedList::new));
        if (p.isEmpty()) {
            info.setError(new InsufficientPositionException(
                    "Insufficient position: " + symbol + ", " + direction + "."));
        } else {
            var px = p.get(0);
            px.setState(UserPosition.FROZEN_CLOSE);
            info.setPositionId(px.getId());
        }
        return info;
    }

    public void close(String positionId, String commissionId, Double price) {
        setCommission(commissionId, price);
        closePosition(positionId, price);
    }

    private void closePosition(String positionId, Double price) {
        var p = this.positions.get(positionId);
        if (p == null) {
            throw new PositionNotFoundException("Position not found: " + positionId + ".");
        } else if (p.getState() != UserPosition.FROZEN_CLOSE) {
            throw new InvalidPositionStateException("Invalid position state: " + p.getState() + ".");
        } else {
            positions.values().remove(p);
            var profit = profit(p, price);
            var id = "S-" + nextId();
            var cash = new UserCash();
            cash.setId(id);
            cash.setCash(profit);
            cash.setSource(UserCash.CLOSE);
            cash.setTradingDay(common.getTradingDay());
            cash.setTime(common.getDateTime());
            cashes.add(cash);
        }
    }

    public OpenInfo freezeOpen(String symbol, String exchange, Character direction, Double price)
            throws IllegalMarginException, IllegalCommissionException {
        var multiple = common.getMultiple(symbol);
        var margin = common.getMargin(symbol, price, direction, Order.OPEN);
        var commission = common.getCommission(symbol, price, direction, Order.OPEN);
        checkMargin(margin);
        checkCommission(commission);
        var info = new OpenInfo();
        var error = checkAvailability(margin, commission);
        if (error != null) {
            info.setError(error);
            return info;
        } else {
            var positionId = addPosition(symbol, exchange, direction, price, multiple, margin);
            var commissionId = addCommission(symbol, direction, Order.OPEN, commission);
            info.setPositionId(positionId);
            info.setCommissionId(commissionId);
            return info;
        }
    }


    private Throwable checkAvailability(Double margin, Double commission) {
        var a = getAvailable();
        if (a < margin + commission) {
            return new InsufficientAvailableException("Insufficient available: " + a + ".");
        } else {
            return null;
        }
    }

    private void checkMargin(Double margin) throws IllegalMarginException {
        if (margin < 0) {
            throw new IllegalMarginException("Illegal margin: " + margin + ".");
        }
    }

    private void checkCommission(Double commission) throws IllegalCommissionException {
        if (commission < 0) {
            throw new IllegalCommissionException("Illegal commission: " + commission + ".");
        }
    }

    private String addCommission(String symbol, Character direction, Character offset, Double commission) {
        var id = "C-" + nextId();
        var c = new UserCommission();
        c.setId(id);
        c.setSymbol(symbol);
        c.setOffset(offset);
        c.setDirection(direction);
        c.setCommission(commission);
        c.setTradingDay(common.getTradingDay());
        c.setTime(common.getDateTime());
        c.setState(UserCommission.FROZEN);
        commissions.put(id, c);
        return id;
    }

    private String addPosition(String symbol, String exchange, Character direction, Double price, Long multiple, Double margin) {
        var id = "P-" + nextId();
        var p = new UserPosition();
        p.setId(id);
        p.setSymbol(symbol);
        p.setExchange(exchange);
        p.setPrice(price);
        p.setMultiple(multiple);
        p.setMargin(margin);
        p.setDirection(direction);
        p.setOpenTradingDay(common.getTradingDay());
        p.setOpenTime(common.getDateTime());
        p.setState(UserPosition.FROZEN_OPEN);
        positions.put(id, p);
        return id;
    }

    public void open(String positionId, String commissionId, Double price) {
        setCommission(commissionId, price);
        openPosition(positionId, price);
    }

    private void setCommission(String commissionId, Double price) {
        var c = commission(commissionId);
        var commission = common.getCommission(c.getSymbol(), price, c.getDirection(), c.getOffset());
        c.setCommission(commission);
        c.setState(UserCommission.NORMAL);
        c.setTime(common.getDateTime());
    }

    private UserCommission commission(String commissionId) {
        var c = this.commissions.get(commissionId);
        if (c == null) {
            throw new CommissionNotFoundException("Commission not found: " + commissionId + ".");
        } else {
            return c;
        }
    }

    private void openPosition(String positionId, Double price) {
        var p = position(positionId);
        if (p.getState() != UserPosition.FROZEN_OPEN) {
            throw new InvalidPositionStateException("Invalid position state: " + p.getState() + ".");
        } else {
            var margin = common.getMargin(p.getSymbol(), price, p.getDirection(), Order.OPEN);
            p.setPrice(price);
            p.setMargin(margin);
            p.setState(UserPosition.NORMAL);
            p.setOpenTime(common.getDateTime());
        }
    }

    private UserPosition position(String positionId) {
        var p = this.positions.get(positionId);
        if (p == null) {
            throw new PositionNotFoundException("Position not found: " + positionId + ".");
        } else {
            return p;
        }
    }

    private Double getAvailable() {
        return getDynamicBalance() - getTotalMargin();
    }

    public Double getTotalMargin() {
        return selectMargin(UserPosition.NORMAL) + selectMargin(UserPosition.FROZEN_CLOSE);
    }

    private Double selectMargin(Character state) {
        return positions.values().stream().filter(position -> position.getState() == state)
                        .mapToDouble(position -> position.getMargin()).sum();
    }

    public Double getTotalFrozenMargin() {
        return selectMargin(UserPosition.FROZEN_OPEN);
    }

    private Double getDynamicBalance() {
        return balance.getBalance() + getBalanceChange();
    }

    public Double getTotalCloseProfit() {
        return cashes.stream().filter(cash -> cash.getSource() == UserCash.CLOSE)
                     .mapToDouble(UserCash::getCash).sum();
    }

    public Double getTotalCommission() {
        return selectCommission(UserCommission.NORMAL);
    }

    private Double selectCommission(Character state) {
        return commissions.values().stream()
                          .filter(commission -> commission.getState() == state)
                          .mapToDouble(UserCommission::getCommission).sum();
    }

    public Double getTotalFrozenCommission() {
        return selectCommission(UserCommission.FROZEN);
    }

    public Double getTotalPositionProfit() {
        return positions.values().stream().mapToDouble(position -> profit(position)).sum();
    }

    private Double getBalanceChange() {
        var totalCash = getTotalDeposit() - getTotalWithdraw();
        totalCash += getTotalPositionProfit() - getTotalCommission();
        return totalCash;
    }

    public Double getTotalDeposit() {
        return cashes.stream().filter(cash -> cash.getSource() == UserCash.DEPOSIT)
                     .mapToDouble(UserCash::getCash).sum();
    }

    public Double getTotalWithdraw() {
        return cashes.stream().filter(cash -> cash.getSource() == UserCash.WITHDRAW)
                     .mapToDouble(UserCash::getCash).sum();
    }

    private double profit(UserPosition position) {
        double curPrice = common.getPrice(position.getSymbol());
        return profit(position, curPrice);
    }

    public static double profit(UserPosition position, double currentPrice) {
        var s = position.getState();
        if (s != UserPosition.NORMAL && s != UserPosition.FROZEN_CLOSE) {
            return .0D;
        }
        double profit;
        double openPrice = position.getPrice();
        long mul = position.getMultiple();
        Character direction = position.getDirection();
        if (Objects.equals(direction, UserPosition.LONG)) {
            profit = (currentPrice - openPrice) * mul;
        } else if (Objects.equals(direction, UserPosition.SHORT)) {
            profit = (openPrice - currentPrice) * mul;
        } else {
            throw new IllegalStateException("Invalid position direction: " + direction + ".");
        }
        return profit;
    }

    public UserBalance getBalance() {
        var b = new UserBalance();
        copyBalance(b, balance);
        return b;
    }

    public Map<String, UserPosition> getPositions() {
        return new HashMap<>(positions);
    }

    public Map<String, UserCommission> getCommissions() {
        return new HashMap<>(commissions);
    }

    public Collection<UserCash> getCashes() {
        return new HashSet<>(cashes);
    }

    private String nextId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
