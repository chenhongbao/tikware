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
    private final UserPersistence persistence;

    public User(UserBalance balance, Collection<UserPosition> positions,
            Collection<UserCommission> commissions, Collection<UserCash> cashes,
            UserPersistence userCommon) {
        this.persistence = userCommon;
        this.cashes.addAll(cashes);
        copyBalance(this.balance, balance);
        copyCommissions(this.commissions, commissions);
        copyPositions(this.positions, positions);
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
            throw new IllegalDirectionError(direction.toString());
        }
        return profit;
    }

    public static String nextId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public UserPersistence getPersistence() {
        return persistence;
    }

    public User settle() {
        clearFrozen();
        var b = new UserBalance();
        b.setId(balance.getId());
        b.setUser(balance.getUser());
        b.setBalance(getDynamicBalance());
        b.setTradingDay(persistence.getTradingDay());
        b.setTime(persistence.getDateTime());
        // Add new user balance to database.
        persistence.alterUserBalance(balance.getUser(), b, UserPersistence.ALTER_ADD);
        return new User(b, positions.values(), commissions.values(), cashes, persistence);
    }

    private void clearFrozen() {
        // Remove frozen, not traded commissions.
        var cit = commissions.values().iterator();
        while (cit.hasNext()) {
            var c = cit.next();
            if (c.getState() == UserCommission.FROZEN) {
                cit.remove();
                // Remove commission from database.
                persistence.alterUserCommission(balance.getUser(), c, UserPersistence.ALTER_DELETE);
            }
        }
        // Remove frozen, not open positions.
        // Set frozen, not closed position to normal.
        var pit = positions.values().iterator();
        while (pit.hasNext()) {
            var p = pit.next();
            if (p.getState() == UserPosition.FROZEN_OPEN) {
                pit.remove();
                // Remove frozen open position from database.
                persistence.alterUserPosition(balance.getUser(), p, UserPersistence.ALTER_DELETE);
            } else if (p.getState() == UserPosition.FROZEN_CLOSE) {
                p.setState(UserPosition.NORMAL);
                // Update position state in database.
                persistence.alterUserPosition(balance.getUser(), p, UserPersistence.ALTER_UPDATE);
            }
        }
    }

    public void undo(OpenInfo info) {
        removePosition(info.getPositionId());
        removeCommission(info.getCommissionId());
    }

    private void removePosition(String positionId) {
        var p = positions.remove(positionId);
        if (p != null) {
            // Remove position from database.
            persistence.alterUserPosition(balance.getUser(), p, UserPersistence.ALTER_DELETE);
        } else {
            throw new PositionNotFoundError(positionId);
        }
    }

    public void undo(CloseInfo info) {
        var p = position(info.getPositionId());
        p.setState(UserPosition.NORMAL);
        // Update position state.
        persistence.alterUserPosition(balance.getUser(), p, UserPersistence.ALTER_UPDATE);
        removeCommission(info.getCommissionId());
    }

    private void removeCommission(String commissionId) {
        var c = commissions.remove(commissionId);
        if (c != null) {
            // Remove commission from database.
            persistence.alterUserCommission(balance.getUser(), c, UserPersistence.ALTER_DELETE);
        } else {
            throw new CommissionNotFoundError(commissionId);
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
        to.setTradingDay(persistence.getTradingDay());
        to.setTime(persistence.getDateTime());
    }

    private void copyPositions(Map<String, UserPosition> to, Collection<UserPosition> from) {
        if (from == null || from.isEmpty()) {
            return;
        }
        from.forEach(position -> to.put(position.getId(), position));
    }

    public CloseInfo freezeClose(String user, String symbol, Character direction, Double price)
            throws IllegalCommissionError {
        checkUser(user);
        var positionDirection = closeDirection(direction);
        var commission = persistence.getCommission(symbol, price, positionDirection,
                Order.CLOSE);
        checkCommission(commission);
        var info = findPosition(symbol, positionDirection);
        if (info.getError() != null) {
            return info;
        } else {
            var commissionId = addCommission(user, symbol, positionDirection, Order.CLOSE,
                    commission);
            info.setCommissionId(commissionId);
            return info;
        }
    }

    private Character closeDirection(Character direction) {
        if (Objects.equals(Order.BUY, direction)) {
            return UserPosition.SHORT;
        } else if (Objects.equals(Order.SELL, direction)) {
            return UserPosition.LONG;
        } else {
            throw new IllegalDirectionError(direction.toString());
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
            info.setError(new InsufficientPositionError(symbol + "|" + direction.toString()));
        } else {
            var px = p.get(0);
            px.setState(UserPosition.FROZEN_CLOSE);
            // Update position state in database.
            persistence.alterUserPosition(balance.getUser(), px, UserPersistence.ALTER_UPDATE);
            info.setPositionId(px.getId());
        }
        return info;
    }

    public void close(String user, String positionId, String commissionId, Double price) {
        checkUser(user);
        setCommission(commissionId, price);
        closePosition(user, positionId, price);
    }

    private void checkUser(String user) {
        if (!balance.getUser().equalsIgnoreCase(user)) {
            throw new WrongUserError(user + "|" + balance.getUser());
        }
    }

    private void closePosition(String user, String positionId, Double price) {
        var p = this.positions.get(positionId);
        if (p == null) {
            throw new PositionNotFoundError(positionId);
        } else if (p.getState() != UserPosition.FROZEN_CLOSE) {
            throw new InvalidPositionStateError(p.getState().toString());
        } else {
            positions.values().remove(p);
            //Remove position from database.
            persistence.alterUserPosition(balance.getUser(), p, UserPersistence.ALTER_DELETE);
            var profit = profit(p, price);
            var id = "S-" + nextId();
            var cash = new UserCash();
            cash.setId(id);
            cash.setCash(profit);
            cash.setSource(UserCash.CLOSE);
            cash.setUser(user);
            cash.setTradingDay(persistence.getTradingDay());
            cash.setTime(persistence.getDateTime());
            cashes.add(cash);
            // Add cash to database.
            persistence.alterUserCash(balance.getUser(), cash, UserPersistence.ALTER_ADD);
        }
    }

    public OpenInfo freezeOpen(String user, String symbol, String exchange, Character direction,
            Double price) throws IllegalMarginError, IllegalCommissionError {
        checkUser(user);
        var positionDirection = positionDirection(direction);
        var multiple = persistence.getMultiple(symbol);
        var margin = persistence.getMargin(symbol, price, positionDirection, Order.OPEN);
        var commission = persistence.getCommission(symbol, price, positionDirection, Order.OPEN);
        checkMargin(margin);
        checkCommission(commission);
        var info = new OpenInfo();
        var error = checkAvailability(margin, commission);
        if (error != null) {
            info.setError(error);
            return info;
        } else {
            var positionId = addPosition(user, symbol, exchange, positionDirection, price,
                    multiple, margin);
            var commissionId = addCommission(user, symbol, positionDirection, Order.OPEN,
                    commission);
            info.setPositionId(positionId);
            info.setCommissionId(commissionId);
            return info;
        }
    }

    private Character positionDirection(Character direction) {
        if (Objects.equals(direction, Order.BUY)) {
            return UserPosition.LONG;
        } else if (Objects.equals(direction, Order.SELL)) {
            return UserPosition.SHORT;
        } else {
            throw new IllegalDirectionError(direction.toString());
        }
    }

    private Throwable checkAvailability(Double margin, Double commission) {
        var a = getAvailable();
        var needed = margin + commission;
        if (a < needed) {
            return new InsufficientAvailableError(a + "|" + needed);
        } else {
            return null;
        }
    }

    private void checkMargin(Double margin) throws IllegalMarginError {
        if (margin < 0) {
            throw new IllegalMarginError(margin.toString());
        }
    }

    private void checkCommission(Double commission) throws IllegalCommissionError {
        if (commission < 0) {
            throw new IllegalCommissionError(commission.toString());
        }
    }

    private String addCommission(String user, String symbol, Character direction,
            Character offset, Double commission) {
        var id = "C-" + nextId();
        var c = new UserCommission();
        c.setId(id);
        c.setUser(user);
        c.setSymbol(symbol);
        c.setOffset(offset);
        c.setDirection(direction);
        c.setCommission(commission);
        c.setTradingDay(persistence.getTradingDay());
        c.setTime(persistence.getDateTime());
        c.setState(UserCommission.FROZEN);
        commissions.put(id, c);
        // Add commissions to database.
        persistence.alterUserCommission(balance.getUser(), c, UserPersistence.ALTER_ADD);
        return id;
    }

    private String addPosition(String user, String symbol, String exchange, Character direction,
            Double price, Long multiple, Double margin) {
        var id = "P-" + nextId();
        var p = new UserPosition();
        p.setId(id);
        p.setUser(user);
        p.setSymbol(symbol);
        p.setExchange(exchange);
        p.setPrice(price);
        p.setMultiple(multiple);
        p.setMargin(margin);
        p.setDirection(direction);
        p.setOpenTradingDay(persistence.getTradingDay());
        p.setOpenTime(persistence.getDateTime());
        p.setState(UserPosition.FROZEN_OPEN);
        positions.put(id, p);
        // Add position into database.
        persistence.alterUserPosition(balance.getUser(), p, UserPersistence.ALTER_ADD);
        return id;
    }

    public void open(String user, String positionId, String commissionId, Double price) {
        checkUser(user);
        setCommission(commissionId, price);
        openPosition(positionId, price);
    }

    private void setCommission(String commissionId, Double price) {
        var c = commission(commissionId);
        var commission = persistence.getCommission(c.getSymbol(), price, c.getDirection(), c.getOffset());
        c.setCommission(commission);
        c.setState(UserCommission.NORMAL);
        c.setTime(persistence.getDateTime());
        // Update commission in database.
        persistence.alterUserCommission(balance.getUser(), c, UserPersistence.ALTER_UPDATE);
    }

    private UserCommission commission(String commissionId) {
        var c = this.commissions.get(commissionId);
        if (c == null) {
            throw new CommissionNotFoundError(commissionId);
        } else {
            return c;
        }
    }

    private void openPosition(String positionId, Double price) {
        var p = position(positionId);
        if (p.getState() != UserPosition.FROZEN_OPEN) {
            throw new InvalidPositionStateError(p.getState().toString());
        } else {
            var margin = persistence.getMargin(p.getSymbol(), price, p.getDirection(), Order.OPEN);
            p.setPrice(price);
            p.setMargin(margin);
            p.setState(UserPosition.NORMAL);
            p.setOpenTime(persistence.getDateTime());
            // Update position state in database.
            persistence.alterUserPosition(balance.getUser(), p, UserPersistence.ALTER_UPDATE);
        }
    }

    private UserPosition position(String positionId) {
        var p = this.positions.get(positionId);
        if (p == null) {
            throw new PositionNotFoundError(positionId);
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
        double curPrice = persistence.getPrice(position.getSymbol());
        return profit(position, curPrice);
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
}
