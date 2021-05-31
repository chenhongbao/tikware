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

package org.tikware.bot;

import org.tikware.api.*;
import org.tikware.spi.Datafeed;
import org.tikware.spi.Transaction;
import org.tikware.user.*;

import java.awt.geom.IllegalPathStateException;
import java.util.*;
import java.util.stream.Collectors;

public class BotEnvironment implements Environment {
    private final User user;
    private final LogListener log;
    private final Transaction transaction;
    private final Datafeed datafeed;

    public BotEnvironment(User user, LogListener log, Transaction transaction, Datafeed datafeed) {
        this.user = user;
        this.log = log;
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
                throw new IllegalOffsetError(order.getOffset().toString());
            }
        } catch (Throwable throwable) {
            try {
                listener.onError(throwable);
            } catch (Throwable ignored) {
            }
        }
    }

    private void close(Order order, OrderListener listener) throws IllegalCommissionError {
        List<CloseInfo> infos = freezeClose(order.getUser(),order.getSymbol(), order.getDirection(),
                order.getPrice(), order.getQuantity());
        // Encountering error, all open infos are cleared and the error info is
        // added to collection.
        if (infos.size() == 1 && infos.get(0).getError() != null) {
            listener.onError(infos.get(0).getError());
        } else if (infos.isEmpty()) {
            listener.onError(new IllegalQuantityError("Empty frozen position."));
        } else {
            sendQuote(order, infos, listener);
        }
    }

    private void sendQuote(Order order, List<CloseInfo> infos, OrderListener listener) {
        // Find close today position and build a specific order to close them.
        var todayInfos = findToday(infos, user.getPersistence().getTradingDay());
        var today = formOrder(todayInfos, order, 1);
        // Find yesterday position and build an order for them.
        var ydInfos = findYd(infos, todayInfos);
        var yd = formOrder(ydInfos, order, 2);
        synchronized (transaction) {
            if (today != null) {
                today.setOffset(Order.CLOSE_TODAY);
                transaction.quote(today, new CloseQuoteListener(user, listener, todayInfos));
            }
            if (yd != null) {
                yd.setOffset(Order.CLOSE_YD);
                transaction.quote(yd, new CloseQuoteListener(user, listener, ydInfos));
            }
        }
    }

    private List<CloseInfo> findToday(List<CloseInfo> infos, String tradingDay) {
        var r = new LinkedList<CloseInfo>();
        infos.forEach(info -> {
            var pid = info.getPositionId();
            var p = user.getPositions().get(pid);
            if (p == null) {
                throw new PositionNotFoundError(pid);
            }
            if (p.getOpenTradingDay().equals(tradingDay)) {
                r.add(info);
            }
        });
        return r;
    }

    private List<CloseInfo> findYd(List<CloseInfo> infos, List<CloseInfo> todayInfos) {
        var s = new HashSet<>(todayInfos);
        var r = new LinkedList<CloseInfo>();
        infos.forEach(info -> {
            if (!s.contains(info)) {
                r.add(info);
            }
        });
        return r;
    }

    private Order formOrder(List<CloseInfo> infos, Order order, int subOrder) {
        if (infos.isEmpty()) {
            return null;
        }
        var x = new Order();
        x.setId(order.getId() + "/" + subOrder);
        x.setSymbol(order.getSymbol());
        x.setUser(order.getUser());
        x.setDirection(order.getDirection());
        x.setExchange(order.getExchange());
        x.setTime(order.getTime());
        x.setPrice(order.getPrice());
        x.setQuantity((long)infos.size());
        return x;
    }

    private List<CloseInfo> freezeClose(String u, String symbol, Character direction, Double price,
            Long quantity) throws IllegalCommissionError {
        var r = new LinkedList<CloseInfo>();
        var count = 0;
        while (count++ < quantity) {
            var info = user.freezeClose(u, symbol, direction, price);
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

    private void open(Order order, OrderListener listener) throws IllegalMarginError,
            IllegalCommissionError {
        var infos = freezeOpen(order.getUser(), order.getSymbol(), order.getExchange(),
                order.getDirection(), order.getPrice(), order.getQuantity());
        // Encountering error, all open infos are cleared and the error info is
        // added to collection.
        if (infos.size() == 1 && infos.get(0).getError() != null) {
            listener.onError(infos.get(0).getError());
        } else if (infos.isEmpty()) {
            listener.onError(new IllegalQuantityError("Empty open quantity."));
        } else {
            synchronized (transaction) {
                transaction.quote(order, new OpenQuoteListener(user, listener, infos));
            }
        }
    }

    private List<OpenInfo> freezeOpen(String u, String symbol, String exchange,
            Character direction, Double price, Long quantity)
            throws IllegalMarginError, IllegalCommissionError {
        var r = new LinkedList<OpenInfo>();
        int count = 0;
        while (count++ < quantity) {
            var info = user.freezeOpen(u, symbol, exchange, direction, price);
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
    public void subscribe(String symbol, TickListener tick, CandleListener candle) {
        if (tick != null) {
            datafeed.subscribe(symbol, tick);
        }
        if (candle != null) {
            datafeed.subscribe(symbol, candle);
        }
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
        b.setTime(user.getPersistence().getDateTime());
        b.setTradingDay(user.getPersistence().getTradingDay());
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

    @Override
    public void log(String message, Throwable stacktrace) {
        try {
            log.onLog(message, stacktrace);
        } catch (Throwable throwable) {
            try {
                log.onLog("Logging failed.", throwable);
            } catch (Throwable ignored) {
            }
        }
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
            throw new IllegalPathStateException(s.toString());
        }
        p.setVolume(p.getVolume() + 1);
        p.setMargin(p.getMargin() + m);
        // Update position profit to latest price.
        var price = user.getPersistence().getPrice(position.getSymbol());
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
            p.setTradingDay(user.getPersistence().getTradingDay());
            p.setTime(user.getPersistence().getDateTime());
            ps.add(p);
            return p;
        }
    }
}
