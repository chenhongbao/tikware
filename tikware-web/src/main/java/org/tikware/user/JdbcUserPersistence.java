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

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

public abstract class JdbcUserPersistence implements UserPersistence {
    private static final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss.SSS");
    private Connection dbc;

    /**
     * Provide connection to custom data source.
     *
     * @return {@link Connection}
     */
    public abstract Connection open();

    protected Connection connection() {
        try {
            if (dbc == null || !dbc.isValid(1)) {
                dbc = open();
            }
            return dbc;
        } catch (SQLException error) {
            throw new Error("Failed validating database connection. " + error.getMessage(), error);
        }
    }

    @Override
    public String getTradingDay() {
        ensureTradingDay();
        try (Statement stmt = connection().createStatement()) {
            var rs = stmt.executeQuery("SELECT _TRADING_DAY FROM _TRADING_DAY_TABLE ORDER BY _TIME DESC LIMIT 1");
            if (rs.next()) {
                var day = rs.getString("_TRADING_DAY");
                rs.close();
                return day;
            } else {
                return "";
            }
        } catch (SQLException error) {
            throw new Error("Failed query trading day. " + error.getMessage(), error);
        }
    }

    @Override
    public String getDateTime() {
        return LocalDateTime.now().format(fmt);
    }

    @Override
    public Double getPrice(String symbol) {
        ensurePrice();
        try (PreparedStatement stmt = connection().prepareStatement(
                "SELECT _PRICE FROM _PRICE_TABLE WHERE _SYMBOL=? ORDER BY _TIME DESC LIMIT 1")) {
            stmt.setString(1, symbol);
            var rs = stmt.executeQuery();
            if (rs.next()) {
                var p = rs.getDouble("_PRICE");
                rs.close();
                return p;
            } else {
                return Double.NaN;
            }
        } catch (SQLException error) {
            throw new Error("Failed query price for " + symbol + ". " + error.getMessage(), error);
        }
    }

    @Override
    public Long getMultiple(String symbol) {
        ensureMultiple();
        try (PreparedStatement stmt = connection().prepareStatement(
                "SELECT _MULTIPLE FROM _MULTIPLE_TABLE WHERE _SYMBOL = ? ORDER BY _TIME DESC LIMIT 1")) {
            stmt.setString(1, symbol);
            var rs = stmt.executeQuery();
            if (rs.next()) {
                var m = (long) rs.getInt("_MULTIPLE");
                rs.close();
                return m;
            } else {
                return null;
            }
        } catch (SQLException error) {
            throw new Error("Failed query multiple for " + symbol + ". " + error.getMessage(), error);
        }
    }

    private void ensureMultiple() {
        try {
            if (tableExists("%", "_MULTIPLE_TABLE")) {
                return;
            }
            createTable("CREATE TABLE _MULTIPLE_TABLE (_TIME CHAR(32), _SYMBOL CHAR(128), _MULTIPLE INT)");
        } catch (SQLException error) {
            throw new Error("Failed ensuring multiple table. " + error.getMessage(), error);
        }

    }

    @Override
    public Double getMargin(String symbol, Double price, Character direction, Character offset) {
        ensureMargin();
        var rs = getMarginRatio(symbol, direction, offset);
        if (rs == null) {
            return Double.NaN;
        } else {
            try {
                var type = rs.getString("_TYPE").charAt(0);
                var ratio = rs.getDouble("_RATIO");
                var margin = calcRatioFee(symbol, price, ratio, type);
                rs.close();
                return margin;
            } catch (SQLException error) {
                throw new Error("Failed calculating margin for " + symbol + ". " + error.getMessage(), error);
            }
        }
    }

    private ResultSet getMarginRatio(String symbol, Character direction, Character offset) {
        try {
            PreparedStatement stmt = connection().prepareStatement(
                    "SELECT _RATIO, _TYPE FROM _MARGIN_TABLE WHERE _SYMBOL = ? AND _DIRECTION = ?" +
                    " AND _OFFSET = ?");
            stmt.setString(1, symbol);
            stmt.setString(2, String.valueOf(direction));
            stmt.setString(3, String.valueOf(offset));
            var rs = stmt.executeQuery();
            if (rs.next()) {
                // Need to close result set later.
                return rs;
            } else {
                return null;
            }
        } catch (SQLException error) {
            throw new Error("Failed query margin for " + symbol + ", direction: "
                            + direction + ", offset: " + offset + ". " + error.getMessage(),
                    error);
        }
    }

    private Double calcRatioFee(String symbol, Double price, double ratio, char type) {
        if (Objects.equals(type, RATIO_BY_AMOUNT)) {
            var m = getMultiple(symbol);
            if (m == null) {
                throw new MultipleNotFoundException("Multiple not found for " + symbol + ".");
            } else {
                return price * m * ratio;
            }
        } else if (Objects.equals(type, RATIO_BY_VOLUME)) {
            return ratio;
        } else {
            throw new IllegalRatioTypeException("Illegal ratio type: " + type + ".");
        }
    }

    private void ensureMargin() {
        try {
            if (tableExists("%", "_MARGIN_TABLE")) {
                return;
            }
            createTable("CREATE TABLE _MARGIN_TABLE (_TIME CHAR(32), _SYMBOL CHAR(128), " +
                        "_RATIO DOUBLE, _DIRECTION CHAR(1), _OFFSET CHAR(1), _TYPE CHAR(1))");
        } catch (SQLException error) {
            throw new Error("Failed ensuring margin table. " + error.getMessage(), error);
        }

    }

    @Override
    public Double getCommission(String symbol, Double price, Character direction, Character offset) {
        ensureCommission();
        var rs = getCommissionRatio(symbol, direction, offset);
        if (rs == null) {
            return Double.NaN;
        } else {
            try {
                var type = rs.getString("_TYPE").charAt(0);
                var ratio = rs.getDouble("_RATIO");
                var commission = calcRatioFee(symbol, price, ratio, type);
                rs.close();
                return commission;
            } catch (SQLException error) {
                throw new Error("Failed calculating margin for " + symbol + ". " + error.getMessage(), error);
            }
        }
    }

    private ResultSet getCommissionRatio(String symbol, Character direction, Character offset) {
        try {
            PreparedStatement stmt = connection().prepareStatement(
                    "SELECT _RATIO, _TYPE FROM _COMMISSION_TABLE WHERE _SYMBOL = ? AND _DIRECTION = ?" +
                    " AND _OFFSET = ?");
            stmt.setString(1, symbol);
            stmt.setString(2, String.valueOf(direction));
            stmt.setString(3, String.valueOf(offset));
            var rs = stmt.executeQuery();
            if (rs.next()) {
                // Need to close result set later.
                return rs;
            } else {
                return null;
            }
        } catch (SQLException error) {
            throw new Error("Failed query commission for " + symbol + ", direction: "
                            + direction + ", offset: " + offset + ". " + error.getMessage(),
                    error);
        }
    }

    private void ensureCommission() {
        try {
            if (tableExists("%", "_COMMISSION_TABLE")) {
                return;
            }
            createTable("CREATE TABLE _COMMISSION_TABLE (_TIME CHAR(32), _SYMBOL CHAR(128), " +
                        "_RATIO DOUBLE, _DIRECTION CHAR(1), _OFFSET CHAR(1), _TYPE CHAR(1))");
        } catch (SQLException error) {
            throw new Error("Failed ensuring commission table. " + error.getMessage(), error);
        }
    }

    protected void ensureTradingDay() {
        try {
            if (tableExists("%", "_TRADING_DAY_TABLE")) {
                return;
            }
            createTable("CREATE TABLE _TRADING_DAY_TABLE (_TIME CHAR(32), _TRADING_DAY CHAR(8))");
        } catch (SQLException throwable) {
            throw new Error("Failed ensuring trading day table. " + throwable.getMessage(), throwable);
        }
    }

    protected void ensurePrice() {
        try {
            if (tableExists("%", "_PRICE_TABLE")) {
                return;
            }
            createTable("CREATE TABLE _PRICE_TABLE (_TIME CHAR(32), _SYMBOL CHAR(128), _PRICE DOUBLE)");
        } catch (SQLException throwable) {
            throw new Error("Failed ensuring price table." + throwable.getMessage(), throwable);
        }
    }

    private boolean tableExists(String schema, String table) throws SQLException {
        var meta = connection().getMetaData();
        var t = meta.getTables(null, schema, table, new String[]{"TABLE"});
        return t.next();
    }

    private void createTable(String sql) throws SQLException {
        try (Statement stmt = connection().createStatement()) {
            stmt.execute(sql);
        }
    }

    @Override
    public void addTradingDay(String tradingDay) {
        ensureTradingDay();
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT INTO _TRADING_DAY_TABLE(_TIME, _TRADING_DAY) VALUES (?, ?)")) {
            stmt.setString(1, getDateTime());
            stmt.setString(2, tradingDay);
            stmt.execute();
            var c = stmt.getUpdateCount();
            if (c != 1) {
                throw new Error("Failed adding trading day, affected " + c + " rows.");
            }
        } catch (SQLException error) {
            throw new Error("Failed adding or updating trading day. " + error.getMessage(), error);
        }
    }

    @Override
    public void addOrUpdatePrice(String symbol, Double price) {
        ensurePrice();
        var p = getPrice(symbol);
        if (p.isNaN()) {
            addPrice(symbol, price);
        } else {
            updatePrice(symbol, price);
        }
    }

    private void addPrice(String symbol, Double price) {
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT INTO _PRICE_TABLE (_TIME, _SYMBOL, _PRICE) VALUES (?,?,?)")) {
            stmt.setString(1, getDateTime());
            stmt.setString(2, symbol);
            stmt.setDouble(3, price);
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed adding price for " + symbol);
            }
        } catch (SQLException error) {
            throw new Error("Failed adding price for " + symbol + ". "
                            + error.getMessage(), error);
        }
    }

    private void updatePrice(String symbol, Double price) {
        try (PreparedStatement stmt = connection().prepareStatement(
                "UPDATE _PRICE_TABLE SET _PRICE = ? WHERE _SYMBOL = ?")) {
            stmt.setDouble(1, price);
            stmt.setString(2, symbol);
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed updating price for " + symbol);
            }
        } catch (SQLException error) {
            throw new Error("Failed updating price for " + symbol + ". "
                            + error.getMessage(), error);
        }
    }

    @Override
    public void addOrUpdateMultiple(String symbol, Long multiple) {
        var m = getMultiple(symbol);
        if (m == null) {
            addMultiple(symbol, multiple);
        } else {
            updateMultiple(symbol, multiple);
        }
    }

    private void addMultiple(String symbol, Long multiple) {
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT INTO _MULTIPLE_TABLE (_TIME, _SYMBOL, _MULTIPLE) VALUES (?,?,?)")) {
            stmt.setString(1, getDateTime());
            stmt.setString(2, symbol);
            stmt.setInt(3, multiple.intValue());
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed adding multiple for " + symbol);
            }
        } catch (SQLException error) {
            throw new Error("Failed adding multiple for " + symbol + ". "
                            + error.getMessage(), error);
        }
    }

    private void updateMultiple(String symbol, Long multiple) {
        try (PreparedStatement stmt = connection().prepareStatement(
                "UPDATE _MULTIPLE_TABLE SET _MULTIPLE = ? WHERE _SYMBOL = ?")) {
            stmt.setInt(1, multiple.intValue());
            stmt.setString(2, symbol);
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed updating multiple for " + symbol);
            }
        } catch (SQLException error) {
            throw new Error("Failed updating multiple for " + symbol + ". "
                            + error.getMessage(), error);
        }
    }

    @Override
    public void addOrUpdateMarginRatio(String symbol, Double ratio, Character direction,
            Character offset, Character type) {
        var rs = getMarginRatio(symbol, direction, offset);
        if (rs == null) {
            addMarginRatio(symbol, ratio, direction, offset, type);
        } else {
            updateMarginRatio(symbol, ratio, direction, offset, type);
            try {
                rs.close();
            } catch (SQLException ignored) {
            }
        }
    }

    private void addMarginRatio(String symbol, Double ratio, Character direction,
            Character offset, Character type) {
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT INTO _MARGIN_TABLE (_TIME, _SYMBOL, _RATIO, _DIRECTION, _OFFSET, _TYPE) " +
                "VALUES (?,?,?,?,?,?)")) {
            stmt.setString(1, getDateTime());
            stmt.setString(2, symbol);
            stmt.setDouble(3, ratio);
            stmt.setString(4, String.valueOf(direction));
            stmt.setString(5, String.valueOf(offset));
            stmt.setString(6, String.valueOf(type));
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed adding margin ratio for " + symbol + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed adding margin ratio for " + symbol + ". " +
                            error.getMessage(), error);
        }
    }

    private void updateMarginRatio(String symbol, Double ratio, Character direction,
            Character offset, Character type) {
        try (PreparedStatement stmt = connection().prepareStatement(
                "UPDATE _MARGIN_TABLE SET _RATIO = ?, _TIME = ?, _TYPE = ? " +
                "WHERE _SYMBOL = ? AND _DIRECTION = ? AND _OFFSET = ?")) {
            stmt.setDouble(1, ratio);
            stmt.setString(2, getDateTime());
            stmt.setString(3, String.valueOf(type));
            stmt.setString(4, symbol);
            stmt.setString(5, String.valueOf(direction));
            stmt.setString(6, String.valueOf(offset));
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed updating margin ratio for " + symbol + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed updating margin ratio for " + symbol + ". " +
                            error.getMessage(), error);
        }
    }

    @Override
    public void addOrUpdateCommissionRatio(String symbol, Double ratio, Character direction,
            Character offset, Character type) {
        var rs = getCommissionRatio(symbol, direction, offset);
        if (rs == null) {
            addCommissionRatio(symbol, ratio, direction, offset, type);
        } else {
            updateCommissionRatio(symbol, ratio, direction, offset, type);
            try {
                rs.close();
            } catch (SQLException ignored) {
            }
        }
    }

    private void addCommissionRatio(String symbol, Double ratio, Character direction,
            Character offset, Character type) {
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT INTO _COMMISSION_TABLE (_TIME, _SYMBOL, _RATIO, _DIRECTION, _OFFSET, _TYPE) " +
                "VALUES (?,?,?,?,?,?)")) {
            stmt.setString(1, getDateTime());
            stmt.setString(2, symbol);
            stmt.setDouble(3, ratio);
            stmt.setString(4, String.valueOf(direction));
            stmt.setString(5, String.valueOf(offset));
            stmt.setString(6, String.valueOf(type));
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed adding commission ratio for " + symbol + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed adding commission ratio for " + symbol + ". " +
                            error.getMessage(), error);
        }
    }

    private void updateCommissionRatio(String symbol, Double ratio, Character direction,
            Character offset, Character type) {
        try (PreparedStatement stmt = connection().prepareStatement(
                "UPDATE _COMMISSION_TABLE SET _RATIO = ?, _TIME = ?, _TYPE = ? " +
                "WHERE _SYMBOL = ? AND _DIRECTION = ? AND _OFFSET = ?")) {
            stmt.setDouble(1, ratio);
            stmt.setString(2, getDateTime());
            stmt.setString(3, String.valueOf(type));
            stmt.setString(4, symbol);
            stmt.setString(5, String.valueOf(direction));
            stmt.setString(6, String.valueOf(offset));
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed updating commission ratio for " + symbol + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed updating commission ratio for " + symbol + ". " +
                            error.getMessage(), error);
        }
    }

    @Override
    public UserBalance getUserBalance(String user) {
        var table = ensureUserBalance(user);
        try (PreparedStatement stmt = connection().prepareStatement(
                "SELECT * FROM " + table + " WHERE _USER = ?")) {
            stmt.setString(1, user);
            var rs = stmt.executeQuery();
            if (rs.next()) {
                return buildUserBalance(rs);
            } else {
                return null;
            }
        } catch (SQLException error) {
            throw new Error("Failed query user balance for " + user + ". " + error.getMessage(), error);
        }
    }

    private UserBalance buildUserBalance(ResultSet rs) throws SQLException {
        var b = new UserBalance();
        b.setId(rs.getString("_ID"));
        b.setUser(rs.getString("_USER"));
        b.setBalance(rs.getDouble("_BALANCE"));
        b.setTradingDay(rs.getString("_TRADING_DAY"));
        b.setTime(rs.getString("_TIME"));
        return b;
    }

    @Override
    public void alterUserBalance(String user, UserBalance balance, Character alter) {
        ensureUserBalance(user);
        if (Objects.equals(alter, ALTER_ADD)) {
            addUserBalance(user, balance);
        } else if (Objects.equals(alter, ALTER_UPDATE)) {
            updateUserBalance(user, balance);
        } else if (Objects.equals(alter, ALTER_DELETE)) {
            deleteUserBalance(user, balance);
        } else {
            throw new Error("Unsupported data operation: " + alter + ".");
        }
    }

    private void addUserBalance(String user, UserBalance balance) {
        var table = userTableName(user, "_USER_BALANCE_TABLE");
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT INTO " + table + " (_ID, _USER, _BALANCE, _TRADING_DAY, _TIME) " +
                "VALUES (?,?,?,?,?)")) {
            stmt.setString(1, balance.getId());
            stmt.setString(2, balance.getUser());
            stmt.setDouble(3, balance.getBalance());
            stmt.setString(4, balance.getTradingDay());
            stmt.setString(5, balance.getTime());
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed adding user balance for " + user + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed adding user balance for " + user + ".", error);
        }
    }

    private void updateUserBalance(String user, UserBalance balance) {
        var table = userTableName(user, "_USER_BALANCE_TABLE");
        try (PreparedStatement stmt = connection().prepareStatement(
                "UPDATE " + table + " SET _BALANCE = ?,_TRADING_DAY = ?, _TIME = ? " +
                "WHERE _ID = ? AND _USER = ?")) {
            stmt.setDouble(1, balance.getBalance());
            stmt.setString(2, balance.getTradingDay());
            stmt.setString(3, balance.getTime());
            stmt.setString(4, balance.getId());
            stmt.setString(5, balance.getUser());
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed updating user balance for " + user + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed updating user balance for " + user + ".", error);
        }
    }

    private void deleteUserBalance(String user, UserBalance balance) {
        var table = userTableName(user, "_USER_BALANCE_TABLE");
        try (PreparedStatement stmt = connection().prepareStatement(
                "DELETE FROM " + table + " WHERE _ID = ? AND _USER = ?")) {
            stmt.setString(1, balance.getId());
            stmt.setString(2, balance.getUser());
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed deleting user balance for " + user + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed deleting user balance for " + user + ".", error);
        }
    }

    private String ensureUserBalance(String user) {
        try {
            var table = userTableName(user, "_USER_BALANCE_TABLE");
            if (tableExists("%", table)) {
                return table;
            }
            createTable("CREATE TABLE " + table + " (_ID CHAR(128), _USER CHAR(128), " +
                        "_BALANCE DOUBLE, _TRADING_DAY CHAR(8), _TIME CHAR(32))");
            return table;
        } catch (SQLException error) {
            throw new Error("Failed ensuring user balance table. " + error.getMessage(), error);
        }
    }

    private String userTableName(String user, String table) {
        // Need remove all non alphabetic or non numeric characters.
        return "_" + user.replaceAll("[^a-zA-Z0-9]", "").toUpperCase() + table;
    }


    @Override
    public Collection<UserPosition> getUserPositions(String user) {
        var table = ensureUserPosition(user);
        try (PreparedStatement stmt = connection().prepareStatement(
                "SELECT * FROM " + table + " WHERE _USER = ?")) {
            stmt.setString(1, user);
            var rs = stmt.executeQuery();
            var r = new HashSet<UserPosition>();
            while (rs.next()) {
                r.add(buildUserPosition(rs));
            }
            return r;
        } catch (SQLException error) {
            throw new Error("Failed querying user positions for " + user + ". "
                            + error.getMessage(), error);
        }
    }

    private UserPosition buildUserPosition(ResultSet rs) throws SQLException {
        var p = new UserPosition();
        p.setId(rs.getString("_ID"));
        p.setUser(rs.getString("_USER"));
        p.setSymbol(rs.getString("_SYMBOL"));
        p.setExchange(rs.getString("_EXCHANGE"));
        p.setPrice(rs.getDouble("_PRICE"));
        p.setMultiple((long) rs.getInt("_MULTIPLE"));
        p.setMargin(rs.getDouble("_MARGIN"));
        p.setDirection(rs.getString("_DIRECTION").charAt(0));
        p.setOpenTradingDay(rs.getString("_OPEN_TRADING_DAY"));
        p.setOpenTime(rs.getString("_OPEN_TIME"));
        p.setState(rs.getString("_STATE").charAt(0));
        return p;
    }

    @Override
    public void alterUserPosition(String user, UserPosition position, Character alter) {
        ensureUserPosition(user);
        if (Objects.equals(alter, ALTER_ADD)) {
            addUserPosition(user, position);
        } else if (Objects.equals(alter, ALTER_UPDATE)) {
            updateUserPosition(user, position);
        } else if (Objects.equals(alter, ALTER_DELETE)) {
            deleteUserPosition(user, position);
        } else {
            throw new Error("Unsupported data operation: " + alter + ".");
        }
    }

    private void addUserPosition(String user, UserPosition position) {
        var table = userTableName(user, "_USER_POSITION_TABLE");
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT INTO " + table + " (_ID, _USER, _SYMBOL, _EXCHANGE, _PRICE, " +
                "_MULTIPLE, _MARGIN, _DIRECTION, _OPEN_TRADING_DAY, _OPEN_TIME, " +
                "_STATE) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
            stmt.setString(1, position.getId());
            stmt.setString(2, position.getUser());
            stmt.setString(3, position.getSymbol());
            stmt.setString(4, position.getExchange());
            stmt.setDouble(5, position.getPrice());
            stmt.setInt(6, position.getMultiple().intValue());
            stmt.setDouble(7, position.getMargin());
            stmt.setString(8, String.valueOf(position.getDirection()));
            stmt.setString(9, position.getOpenTradingDay());
            stmt.setString(10, position.getOpenTime());
            stmt.setString(11, String.valueOf(position.getState()));
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed adding user position for " + user + ". ");
            }
        } catch (SQLException error) {
            throw new Error("Failed adding user position for " + user + ". "
                            + error.getMessage(), error);
        }
    }

    private void updateUserPosition(String user, UserPosition position) {
        var table = userTableName(user, "_USER_POSITION_TABLE");
        try (PreparedStatement stmt = connection().prepareStatement(
                "UPDATE " + table + " SET _SYMBOL = ?, _EXCHANGE = ?, _PRICE = ?, " +
                "_MULTIPLE = ?, _MARGIN = ?, _DIRECTION = ?, _OPEN_TRADING_DAY = ?," +
                " _OPEN_TIME = ?, _STATE = ? WHERE _ID = ? AND _USER = ?")) {
            stmt.setString(1, position.getSymbol());
            stmt.setString(2, position.getExchange());
            stmt.setDouble(3, position.getPrice());
            stmt.setInt(4, position.getMultiple().intValue());
            stmt.setDouble(5, position.getMargin());
            stmt.setString(6, String.valueOf(position.getDirection()));
            stmt.setString(7, position.getOpenTradingDay());
            stmt.setString(8, position.getOpenTime());
            stmt.setString(9, String.valueOf(position.getState()));
            stmt.setString(10, position.getId());
            stmt.setString(11, position.getUser());
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed updating user position for " + user + ". ");
            }
        } catch (SQLException error) {
            throw new Error("Failed updating user position for " + user + ". "
                            + error.getMessage(), error);
        }
    }

    private void deleteUserPosition(String user, UserPosition position) {
        var table = userTableName(user, "_USER_POSITION_TABLE");
        try (PreparedStatement stmt = connection().prepareStatement(
                "DELETE FROM " + table + " WHERE _ID = ? AND _USER = ?")) {
            stmt.setString(1, position.getId());
            stmt.setString(2, position.getUser());
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed deleting user position for " + user + ". ");
            }
        } catch (SQLException error) {
            throw new Error("Failed deleting user position for " + user + ". "
                            + error.getMessage(), error);
        }
    }

    private String ensureUserPosition(String user) {
        try {
            var table = userTableName(user, "_USER_POSITION_TABLE");
            if (tableExists("%", table)) {
                return table;
            }
            createTable("CREATE TABLE " + table + " (_ID CHAR(128), _USER CHAR(128), " +
                        "_SYMBOL CHAR(128), _EXCHANGE CHAR(32), _PRICE DOUBLE, _MULTIPLE INT," +
                        " _MARGIN DOUBLE, _DIRECTION CHAR(1), _OPEN_TRADING_DAY CHAR(8)," +
                        " _OPEN_TIME CHAR(32), _STATE CHAR(1))");
            return table;
        } catch (SQLException error) {
            throw new Error("Failed ensuring user position table. " + error.getMessage(), error);
        }
    }

    @Override
    public Collection<UserCash> getUserCashes(String user) {
        var table = ensureUserCash(user);
        try (PreparedStatement stmt = connection().prepareStatement(
                "SELECT * FROM " + table + " WHERE _USER = ?")) {
            stmt.setString(1, user);
            var rs = stmt.executeQuery();
            var r = new HashSet<UserCash>();
            while (rs.next()) {
                r.add(buildUserCash(rs));
            }
            return r;
        } catch (SQLException error) {
            throw new Error("Failed querying user cash for " + user + ". " +
                            error.getMessage(), error);
        }
    }

    private UserCash buildUserCash(ResultSet rs) throws SQLException {
        var c = new UserCash();
        c.setId(rs.getString("_ID"));
        c.setUser(rs.getString("_USER"));
        c.setCash(rs.getDouble("_CASH"));
        c.setSource(rs.getString("_SOURCE").charAt(0));
        c.setTradingDay(rs.getString("_TRADING_DAY"));
        c.setTime(rs.getString("_TIME"));
        return c;
    }

    @Override
    public void alterUserCash(String user, UserCash cash, Character alter) {
        ensureUserCash(user);
        if (Objects.equals(alter, ALTER_ADD)) {
            addUserCash(user, cash);
        } else if (Objects.equals(alter, ALTER_UPDATE)) {
            updateUserCash(user, cash);
        } else if (Objects.equals(alter, ALTER_DELETE)) {
            deleteUserCash(user, cash);
        } else {
            throw new Error("Unsupported data operation: " + alter + ".");
        }
    }

    private void addUserCash(String user, UserCash cash) {
        var table = userTableName(user, "_USER_CASH_TABLE");
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT INTO " + table + " (_ID, _USER, _CASH, _SOURCE, _TRADING_DAY, _TIME)" +
                " VALUES (?,?,?,?,?,?)")) {
            stmt.setString(1, cash.getId());
            stmt.setString(2, cash.getUser());
            stmt.setDouble(3, cash.getCash());
            stmt.setString(4, String.valueOf(cash.getSource()));
            stmt.setString(5, cash.getTradingDay());
            stmt.setString(6, cash.getTime());
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed adding user cash for " + user + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed adding user cash for " + user + ". " +
                            error.getMessage(), error);
        }
    }

    private void updateUserCash(String user, UserCash cash) {
        var table = userTableName(user, "_USER_CASH_TABLE");
        try (PreparedStatement stmt = connection().prepareStatement(
                "UPDATE " + table + " SET _CASH = ?, _SOURCE = ?, _TRADING_DAY = ?, " +
                "_TIME = ? WHERE _ID = ? AND _USER = ?")) {
            stmt.setDouble(1, cash.getCash());
            stmt.setString(2, String.valueOf(cash.getSource()));
            stmt.setString(3, cash.getTradingDay());
            stmt.setString(4, cash.getTime());
            stmt.setString(5, cash.getId());
            stmt.setString(6, cash.getUser());
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed updating user cash for " + user + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed updating user cash for " + user + ". " +
                            error.getMessage(), error);
        }
    }

    private void deleteUserCash(String user, UserCash cash) {
        var table = userTableName(user, "_USER_CASH_TABLE");
        try (PreparedStatement stmt = connection().prepareStatement(
                "DELETE FROM " + table + " WHERE _ID = ? AND _USER = ?")) {
            stmt.setString(1, cash.getId());
            stmt.setString(2, cash.getUser());
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed deleting user cash for " + user + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed updating user cash for " + user + ". " +
                            error.getMessage(), error);
        }
    }

    private String ensureUserCash(String user) {
        try {
            var table = userTableName(user, "_USER_CASH_TABLE");
            if (tableExists("%", table)) {
                return table;
            }
            createTable("CREATE TABLE " + table + " (_ID CHAR(128), _USER CHAR(128), " +
                        "_CASH DOUBLE, _SOURCE CHAR(1), _TRADING_DAY CHAR(8)," +
                        " _TIME CHAR(32))");
            return table;
        } catch (SQLException error) {
            throw new Error("Failed ensuring user position table. " + error.getMessage(), error);
        }
    }

    @Override
    public Collection<UserCommission> getUserCommissions(String user) {
        var table = ensureUserCommission(user);
        try (PreparedStatement stmt = connection().prepareStatement(
                "SELECT * FROM " + table + " WHERE _USER = ?")) {
            stmt.setString(1, user);
            var rs = stmt.executeQuery();
            var r = new HashSet<UserCommission>();
            while (rs.next()) {
                r.add(buildUserCommission(rs));
            }
            return r;
        } catch (SQLException error) {
            throw new Error("Failed querying user commission for " + user + ". " +
                            error.getMessage(), error);
        }
    }

    private UserCommission buildUserCommission(ResultSet rs) throws SQLException {
        var c = new UserCommission();
        c.setId(rs.getString("_ID"));
        c.setUser(rs.getString("_USER"));
        c.setSymbol(rs.getString("_SYMBOL"));
        c.setDirection(rs.getString("_DIRECTION").charAt(0));
        c.setOffset(rs.getString("_OFFSET").charAt(0));
        c.setCommission(rs.getDouble("_COMMISSION"));
        c.setTradingDay(rs.getString("_TRADING_DAY"));
        c.setTime(rs.getString("_TIME"));
        c.setState(rs.getString("_STATE").charAt(0));
        return c;
    }

    @Override
    public void alterUserCommission(String user, UserCommission commission, Character alter) {
        ensureUserCommission(user);
        if (Objects.equals(alter, ALTER_ADD)) {
            addUserCommission(user, commission);
        } else if (Objects.equals(alter, ALTER_UPDATE)) {
            updateUserCommission(user, commission);
        } else if (Objects.equals(alter, ALTER_DELETE)) {
            deleteUserCommission(user, commission);
        } else {
            throw new Error("Unsupported data operation: " + alter + ".");
        }
    }

    private void addUserCommission(String user, UserCommission commission) {
        var table = userTableName(user, "_USER_COMMISSION_TABLE");
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT INTO " + table + " (_ID, _USER, _SYMBOL, _DIRECTION, _OFFSET," +
                " _COMMISSION, _TRADING_DAY, _TIME, _STATE) VALUES (?,?,?,?,?,?,?,?,?)")) {
            stmt.setString(1, commission.getId());
            stmt.setString(2, commission.getUser());
            stmt.setString(3, commission.getSymbol());
            stmt.setString(4, String.valueOf(commission.getDirection()));
            stmt.setString(5, String.valueOf(commission.getOffset()));
            stmt.setDouble(6, commission.getCommission());
            stmt.setString(7, commission.getTradingDay());
            stmt.setString(8, commission.getTime());
            stmt.setString(9, String.valueOf(commission.getState()));
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed querying user commission for " + user + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed querying user commission for " + user + ". " +
                            error.getMessage(), error);
        }
    }

    private void updateUserCommission(String user, UserCommission commission) {
        var table = userTableName(user, "_USER_COMMISSION_TABLE");
        try (PreparedStatement stmt = connection().prepareStatement(
                "UPDATE " + table + " SET _SYMBOL = ?, _DIRECTION = ?, _OFFSET = ?, " +
                "_COMMISSION = ?, _TRADING_DAY = ?, _TIME = ?, _STATE = ? " +
                "WHERE _ID = ? AND _USER = ?")) {
            stmt.setString(1, commission.getSymbol());
            stmt.setString(2, String.valueOf(commission.getDirection()));
            stmt.setString(3, String.valueOf(commission.getOffset()));
            stmt.setDouble(4, commission.getCommission());
            stmt.setString(5, commission.getTradingDay());
            stmt.setString(6, commission.getTime());
            stmt.setString(7, String.valueOf(commission.getState()));
            stmt.setString(8, commission.getId());
            stmt.setString(9, commission.getUser());
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed updating user commission for " + user + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed updating user commission for " + user + ". " +
                            error.getMessage(), error);
        }
    }

    private void deleteUserCommission(String user, UserCommission commission) {
        var table = userTableName(user, "_USER_COMMISSION_TABLE");
        try (PreparedStatement stmt = connection().prepareStatement(
                "DELETE FROM " + table + " WHERE _ID = ? AND _USER = ?")) {
            stmt.setString(1, commission.getId());
            stmt.setString(2, commission.getUser());
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed deleting user commission for " + user + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed deleting user commission for " + user + ". " +
                            error.getMessage(), error);
        }
    }

    private String ensureUserCommission(String user) {
        var table = userTableName(user, "_USER_COMMISSION_TABLE");
        try {
            if (tableExists("%", table)) {
                return table;
            }
            createTable("CREATE TABLE " + table + " (_ID CHAR(128), _USER CHAR(128), " +
                        "_SYMBOL CHAR(128), _DIRECTION CHAR(1), _OFFSET CHAR(1), " +
                        "_COMMISSION DOUBLE, _TRADING_DAY CHAR(8), _TIME CHAR(32), " +
                        "_STATE CHAR(1))");
            return table;
        } catch (SQLException error) {
            throw new Error("Failed ensuring user commission for " + user + ". " +
                            error.getMessage(), error);
        }
    }

    @Override
    public Collection<UserInfo> getUserInfos() {
        ensureUserInfo();
        try (Statement stmt = connection().createStatement()) {
            var rs = stmt.executeQuery("SELECT * FROM _USER_INFO_TABLE");
            var r = new HashSet<UserInfo>();
            while (rs.next()) {
                r.add(buildUserInfo(rs));
            }
            return r;
        } catch (SQLException error) {
            throw new Error("Failed querying user info. " + error.getMessage(), error);
        }
    }

    private UserInfo buildUserInfo(ResultSet rs) throws SQLException {
        var u = new UserInfo();
        u.setId(rs.getString("_ID"));
        u.setUser(rs.getString("_USER"));
        u.setPassword(rs.getString("_PASSWORD"));
        u.setNickname(rs.getString("_NICKNAME"));
        u.setPrivilege(rs.getString("_PRIVILEGE").charAt(0));
        u.setJoinTime(rs.getString("_JOIN_TIME"));
        return u;
    }

    @Override
    public void alterUserInfo(UserInfo user, Character alter) {
        ensureUserInfo();
        if (Objects.equals(alter, ALTER_ADD)) {
            addUserInfo(user);
        } else if (Objects.equals(alter, ALTER_UPDATE)) {
            updateUserInfo(user);
        } else if (Objects.equals(alter, ALTER_DELETE)) {
            deleteUserInfo(user);
        } else {
            throw new Error("Unsupported data operation: " + alter + ".");
        }
    }

    private void addUserInfo(UserInfo user) {
        try (PreparedStatement stmt = connection().prepareStatement(
                "INSERT INTO _USER_INFO_TABLE (_ID, _USER, _PASSWORD, _NICKNAME, " +
                "_PRIVILEGE, _JOIN_TIME) VALUES (?,?,?,?,?,?)")) {
            stmt.setString(1, user.getId());
            stmt.setString(2, user.getUser());
            stmt.setString(3, user.getPassword());
            stmt.setString(4, user.getNickname());
            stmt.setString(5, String.valueOf(user.getPrivilege()));
            stmt.setString(6, user.getJoinTime());
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed adding user info for " + user.getUser() + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed adding user info for " + user.getUser() + ". " +
                            error.getMessage(), error);
        }
    }

    private void updateUserInfo(UserInfo user) {
        try (PreparedStatement stmt = connection().prepareStatement(
                "UPDATE _USER_INFO_TABLE SET _PASSWORD = ?, _NICKNAME = ?, _PRIVILEGE = ?, " +
                "_JOIN_TIME = ? WHERE _ID = ? AND _USER = ?")) {
            stmt.setString(1, user.getPassword());
            stmt.setString(2, user.getNickname());
            stmt.setString(3, String.valueOf(user.getPrivilege()));
            stmt.setString(4, user.getJoinTime());
            stmt.setString(5, user.getId());
            stmt.setString(6, user.getUser());
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed updating user info for " + user.getUser() + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed updating user info for " + user.getUser() + ". " +
                            error.getMessage(), error);
        }
    }

    private void deleteUserInfo(UserInfo user) {
        try (PreparedStatement stmt = connection().prepareStatement(
                "DELETE FROM _USER_INFO_TABLE WHERE _ID = ? AND _USER = ?")) {
            stmt.setString(1, user.getId());
            stmt.setString(2, user.getUser());
            stmt.execute();
            if (stmt.getUpdateCount() != 1) {
                throw new Error("Failed deleting user info for " + user.getUser() + ".");
            }
        } catch (SQLException error) {
            throw new Error("Failed deleting user info for " + user.getUser() + ". " +
                            error.getMessage(), error);
        }
    }

    private void ensureUserInfo() {
        try {
            if (tableExists("%", "_USER_INFO_TABLE")) {
                return;
            }
            createTable("CREATE TABLE \"_USER_INFO_TABLE\" (_ID CHAR(128), _USER CHAR(128), " +
                        "_PASSWORD CHAR(128), _NICKNAME CHAR(128), _PRIVILEGE CHAR(1), " +
                        "_JOIN_TIME CHAR(32))");
        } catch (SQLException error) {
            throw new Error("Failed ensuring user info. " + error.getMessage(), error);
        }
    }
}
