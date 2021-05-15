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

package org.tikware.api.bot;

import org.tikware.api.*;
import org.tikware.spi.Datafeed;
import org.tikware.spi.Transaction;
import org.tikware.user.*;

import java.awt.geom.IllegalPathStateException;
import java.util.*;
import java.util.stream.Collectors;

public class BotContainer implements Environment {
    private final User user;
    private final Transaction transaction;
    private final Datafeed datafeed;

    public BotContainer(User user, Transaction transaction, Datafeed datafeed) {
        this.user = user;
        this.transaction = transaction;
        this.datafeed = datafeed;
    }

    @Override
    public void quote(Order order, OrderListener listener) {
        var offset = order.getOffset();
        try {
            if (offset == Order.OPEN) {
                open(order, listener);
            } else if (offset == Order.CLOSE) {
                close(order, listener);
            } else {
                throw new IllegalOffsetException("Illegal offset: " + offset + ".");
            }
        } catch (Throwable throwable) {
            try {
                listener.onError(throwable);
            } catch (Throwable ignored) {
            }
        }
    }

    private void close(Order order, OrderListener listener) throws IllegalCommissionException {
        List<CloseInfo> infos = freezeClose(order.getSymbol(), order.getDirection(),
                order.getPrice(), order.getQuantity());
        // Encountering error, all open infos are cleared and the error info is
        // added to collection.
        if (infos.size() == 1 && infos.get(0).getError() != null) {
            listener.onError(infos.get(0).getError());
        } else if (infos.isEmpty()) {
            listener.onError(new IllegalQuantityException("Empty close quantity."));
        } else {
            synchronized (transaction) {
                transaction.quote(order, new CloseListener(user, listener, infos));
            }
        }
    }

    private List<CloseInfo> freezeClose(String symbol, Character direction, Double price,
            Long quantity) throws IllegalCommissionException {
        var r = new LinkedList<CloseInfo>();
        var count = 0;
        while (count++ < quantity) {
            var info = user.freezeClose(symbol, direction, price);
            if (info.getError() != null) {
                r.forEach(user::undo);
                r.clear();
                r.add(info);
                break;
            }
            r.add(info);
        }
        return r;
    }

    private void open(Order order, OrderListener listener) throws IllegalMarginException,
            IllegalCommissionException {
        var infos = freezeOpen(order.getSymbol(), order.getExchange(), order.getDirection(),
                order.getPrice(), order.getQuantity());
        // Encountering error, all open infos are cleared and the error info is
        // added to collection.
        if (infos.size() == 1 && infos.get(0).getError() != null) {
            listener.onError(infos.get(0).getError());
        } else if (infos.isEmpty()) {
            listener.onError(new IllegalQuantityException("Empty open quantity."));
        } else {
            synchronized (transaction) {
                transaction.quote(order, new OpenListener(user, listener, infos));
            }
        }
    }

    private List<OpenInfo> freezeOpen(String symbol, String exchange, Character direction,
            Double price, Long quantity)
            throws IllegalMarginException, IllegalCommissionException {
        var r = new LinkedList<OpenInfo>();
        int count = 0;
        while (count++ < quantity) {
            var info = user.freezeOpen(symbol, exchange, direction, price);
            if (info.getError() != null) {
                r.forEach(user::undo);
                r.clear();
                r.add(info);
                break;
            }
            r.add(info);
        }
        return r;
    }

    @Override
    public void subscribe(String symbol, TickListener listener) {
        datafeed.subscribe(symbol, listener);
    }

    @Override
    public void subscribe(String symbol, int minutes, CandleListener listener) {
        datafeed.subscribe(symbol, minutes, listener);
    }

    @Override
    public Balance getBalance() {
        var b = new Balance();
        b.setPreBalance(user.getBalance().getBalance());
        b.setCommission(user.getTotalCommission());
        b.setFrozenCommission(user.getTotalFrozenCommission());
        b.setCloseProfit(user.getTotalCloseProfit());
        b.setMargin(user.getTotalMargin());
        b.setFrozenMargin(user.getTotalFrozenMargin());
        b.setPositionProfit(user.getTotalPositionProfit());
        b.setUser(user.getBalance().getUser());
        b.setDeposit(user.getTotalDeposit());
        b.setWithdraw(user.getTotalWithdraw());
        b.setBalance(user.getBalance().getBalance() + b.getDeposit() - b.getWithdraw()
                     + b.getPositionProfit() + b.getCloseProfit() - b.getCommission());
        b.setAvailable(b.getBalance() - b.getMargin() - b.getFrozenMargin()
                       - b.getFrozenCommission());
        b.setTime(user.getCommon().getDateTime());
        b.setTradingDay(user.getCommon().getTradingDay());
        return b;
    }

    @Override
    public Collection<Position> getPositions(String symbol) {
        var ps = new HashSet<Position>();
        user.getPositions().values().stream()
            .filter(position -> symbol.isBlank() ||
                                position.getSymbol().equalsIgnoreCase(symbol))
            .collect(Collectors.toCollection(HashSet::new))
            .forEach(position -> {
                var p = findPosition(ps, position.getSymbol(), position.getDirection());
                updatePosition(p, position);
            });
        return ps;
    }

    private void updatePosition(Position p, UserPosition position) {
        var s = position.getState();
        var m = position.getMargin();
        if (Objects.equals(s, UserPosition.FROZEN_OPEN)) {
            p.setOpeningVolume(p.getOpeningVolume() + 1);
            p.setOpeningMargin(p.getOpeningMargin() + m);
        } else if (Objects.equals(s, UserPosition.FROZEN_CLOSE)) {
            p.setClosingVolume(p.getClosingVolume() + 1);
            p.setClosingMargin(p.getClosingMargin() + m);
        } else {
            throw new IllegalPathStateException("Illegal position state: " + s + ".");
        }
        p.setVolume(p.getVolume() + 1);
        p.setMargin(p.getMargin() + m);
        // Update position profit to latest price.
        var price = user.getCommon().getPrice(position.getSymbol());
        var profit = User.profit(position, price);
        p.setPositionProfit(p.getPositionProfit() + profit);
    }

    private Position findPosition(Collection<Position> ps, String symbol, Character direction) {
        var c = ps.stream().filter(position -> Objects.equals(position.getDirection(), direction))
                  .iterator();
        if (c.hasNext()) {
            return c.next();
        } else {
            var p = new Position();
            p.setSymbol(symbol);
            p.setDirection(direction);
            p.setTradingDay(user.getCommon().getTradingDay());
            p.setTime(user.getCommon().getDateTime());
            ps.add(p);
            return p;
        }
    }
}
