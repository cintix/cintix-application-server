package dk.cintix.tinyserver.demo.managers;

import dk.cintix.tinyserver.demo.entities.Channel;
import dk.cintix.tinyserver.jdbc.DataSourceManager;
import dk.cintix.tinyserver.jdbc.EntityManager;
import dk.cintix.tinyserver.jdbc.annotations.InjectConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.sql.DataSource;

/**
 * @author hjep
 */
public class ChannelManager extends Channel {

    private static final Logger LOGGER = Logger.getLogger(ChannelManager.class.getName());
    private final DataSource dataSource = DataSourceManager.getInstance("jdbc/epg");

    private final static String CREATE_CHANNEL_SQL = "INSERT INTO channel (provider_id, is_tv_channel, country_code, viewcode, name, url, external_id, type) VALUES (?,?,?,?,?,?,?,?) ".intern();
    private final static String UPDATE_CHANNEL_SQL = "UPDATE channel SET name = ?, url = ?, viewcode = ?, \"enable\"=? WHERE id = ? ".intern();
    private final static String UPDATE_CHANNEL_LABEL_SQL = "UPDATE channel SET label = ? WHERE id = ? ".intern();
    private final static String REMOVE_CHANNEL_SQL = "DELETE FROM channel WHERE id = ?".intern();
    private final static String SELECT_CHANNEL_BY_ID_SQL = "SELECT * FROM channel WHERE id = ?".intern();
    private final static String SELECT_CHANNEL_ALL_SQL = "SELECT * FROM channel ORDER BY id DESC".intern();
    private final static String SELECT_CHANNEL_LOGO_MAP = "SELECT * FROM channel_service_image".intern();
    private final static String SELECT_CHANNEL_LOGO_BY_ID = "SELECT * FROM channel_service_image WHERE id = ?".intern();
    private final static String SELECT_NOW_RUNNING_MAP = "SELECT * FROM running".intern();
    private final static String SELECT_SERVICE_ID = "SELECT * FROM channel_mapping WHERE external_id = ? AND provider_id = ?".intern();
    private final static String SELECT_CHANNELS_BY_PROVIDER_SQL = "SELECT * FROM channel WHERE provider_id = ? ORDER BY id DESC".intern();

    @InjectConnection
    private final Connection cachedConnection = null;

    @Override
    public boolean create() {
        try (Connection connection = (cachedConnection != null && !cachedConnection.isClosed()) ? cachedConnection : dataSource.getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(CREATE_CHANNEL_SQL, Statement.RETURN_GENERATED_KEYS)) {
                preparedStatement.setInt(1, getProviderId());
                preparedStatement.setBoolean(2, isIsTVCHannel());
                preparedStatement.setInt(3, getCountryCode());
                preparedStatement.setInt(4, getView());
                preparedStatement.setString(5, getName());
                preparedStatement.setString(6, getUrl());
                preparedStatement.setLong(7, getExternalId());
                preparedStatement.setString(8, getType());

                if (preparedStatement.executeUpdate() > 0) {
                    try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
                        if (resultSet != null && resultSet.next()) {
                            setId(resultSet.getInt(1));
                            return true;
                        } else {
                            return false;
                        }
                    }
                }
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            if (e.toString().toLowerCase().contains("duplicate")) {

                if (getExternalId() > 0) {
                    setId(loadAllExternalId().get(getExternalId()).getId());
                    return true;
                }

            }
            LOGGER.log(Level.SEVERE, "ChannelManager.create() threw an exception", e);
            return false;
        }
    }

    @Override
    public boolean setAlias(String name) {
        try (Connection connection = (cachedConnection != null && !cachedConnection.isClosed()) ? cachedConnection : dataSource.getConnection()) {

            try (PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_CHANNEL_LABEL_SQL)) {
                preparedStatement.setString(1, name);
                preparedStatement.setInt(2, getId());
                int affectedCount = preparedStatement.executeUpdate();
                return (affectedCount > 0);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ChannelManager.setAlias('" + name + "') threw an exception", e);
            return false;
        }
    }

    @Override
    public boolean update() {
        try (Connection connection = (cachedConnection != null && !cachedConnection.isClosed()) ? cachedConnection : dataSource.getConnection()) {

            try (PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_CHANNEL_SQL)) {
                preparedStatement.setString(1, getName());
                preparedStatement.setString(2, getUrl());
                preparedStatement.setInt(3, getView());
                preparedStatement.setBoolean(4, isEnable());
                preparedStatement.setInt(5, getId());

                System.out.println(preparedStatement.toString());
                int affectedCount = preparedStatement.executeUpdate();

                return affectedCount > 0;
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ChannelManager.update() threw an exception", e);
            return false;
        }
    }

    @Override
    public boolean delete(int id) {
        try (Connection connection = (cachedConnection != null && !cachedConnection.isClosed()) ? cachedConnection : dataSource.getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(REMOVE_CHANNEL_SQL)) {
                preparedStatement.setInt(1, id);

                return preparedStatement.executeUpdate() > 0;
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ChannelManager.delete() threw an exception", e);
            return false;
        }
    }

    @Override
    public Channel load(int id) {
        try (Connection connection = (cachedConnection != null && !cachedConnection.isClosed()) ? cachedConnection : dataSource.getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(SELECT_CHANNEL_BY_ID_SQL)) {
                preparedStatement.setInt(1, id);

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        return readEntity(resultSet);
                    }
                }

            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ChannelManager.load() threw an exception", e);
        }
        return null;
    }

    @Override
    public Map<Long, Channel> loadAllExternalId() {
        Map<Long, Channel> map = new LinkedHashMap<>();
        try (Connection connection = (cachedConnection != null && !cachedConnection.isClosed()) ? cachedConnection : dataSource.getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(SELECT_CHANNEL_ALL_SQL)) {
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        Channel readEntity = readEntity(resultSet);
                        if (readEntity.getExternalId() == 0) {
                            continue;
                        }
                        map.put(readEntity.getExternalId(), readEntity);
                    }
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ChannelManager.loadAll() threw an exception", e);
        }
        return map;
    }

    @Override
    public boolean delete() {
        return delete(getId());
    }

    @Override
    public Channel load() {
        return load(getId());
    }

    private Channel readEntity(ResultSet resultSet) throws SQLException {
        Channel channel = EntityManager.create(Channel.class, cachedConnection);

        channel.setId(resultSet.getInt("id"));
        channel.setExternalId(resultSet.getLong("external_id"));
        channel.setType(resultSet.getString("type"));
        channel.setProviderId(resultSet.getInt("provider_id"));
        channel.setCountryCode(resultSet.getInt("country_code"));
        channel.setView(resultSet.getInt("viewcode"));
        channel.setIsTVCHannel(resultSet.getBoolean("is_tv_channel"));
        channel.setEnable((resultSet.getBoolean("enable")));
        channel.setName(resultSet.getString("name"));
        channel.setLabel(resultSet.getString("label"));
        channel.setUrl(resultSet.getString("url"));
        channel.setIdentifier(resultSet.getString("identifier"));

        return channel;
    }

    @Override
    public List<Channel> loadAll() {
        List<Channel> list = new LinkedList<>();
        try (Connection connection = (cachedConnection != null && !cachedConnection.isClosed()) ? cachedConnection : dataSource.getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(SELECT_CHANNEL_ALL_SQL)) {
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        Channel readEntity = readEntity(resultSet);
                        list.add(readEntity);
                    }
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ChannelManager.loadAll() threw an exception", e);
        }
        return list;
    }

    @Override
    public Map<Integer, String> loadChannelLogoMap() {
        Map<Integer, String> logos = new LinkedHashMap<>();
        try (Connection connection = (cachedConnection != null && !cachedConnection.isClosed()) ? cachedConnection : dataSource.getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(SELECT_CHANNEL_LOGO_MAP)) {
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        logos.put(resultSet.getInt("id"), resultSet.getString("url"));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ChannelManager.loadChannelLogoMap() threw an exception", e);
        }
        return logos;
    }

    @Override
    public Map<Integer, String> loadRunningNowMap() {
        Map<Integer, String> logos = new LinkedHashMap<>();
        try (Connection connection = (cachedConnection != null && !cachedConnection.isClosed()) ? cachedConnection : dataSource.getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(SELECT_NOW_RUNNING_MAP)) {
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        logos.put(resultSet.getInt("channel_id"), resultSet.getString("title"));
                    }
                }

            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ChannelManager.loadRunningNowMap() threw an exception", e);
        }
        return logos;
    }

    @Override
    public String getLogoUrl() {
        try (Connection connection = (cachedConnection != null && !cachedConnection.isClosed()) ? cachedConnection : dataSource.getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(SELECT_CHANNEL_LOGO_BY_ID)) {
                preparedStatement.setInt(1, getId());
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getString("url");
                    }
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ChannelManager.load() threw an exception", e);
        }
        return null;
    }

    @Override
    public int getServiceId() {
        try (Connection connection = (cachedConnection != null && !cachedConnection.isClosed()) ? cachedConnection : dataSource.getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(SELECT_SERVICE_ID)) {
                preparedStatement.setLong(1, getExternalId());
                preparedStatement.setInt(2, getProviderId());

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getInt("service_id");
                    }
                }

            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ChannelManager.getServiceId() threw an exception", e);
        }
        return 0;
    }

    @Override
    public Map<String, Channel> loadAllWhatsOnChannels() {
        Map<String, Channel> map = new LinkedHashMap<>();
        int whatsOnId = 2;
        try (Connection connection = (cachedConnection != null && !cachedConnection.isClosed()) ? cachedConnection : dataSource.getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(SELECT_CHANNELS_BY_PROVIDER_SQL)) {

                preparedStatement.setInt(1, whatsOnId);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        Channel readEntity = readEntity(resultSet);
                        if (readEntity.getExternalId() == 0) {
                            continue;
                        }
                        map.put(readEntity.getIdentifier(), readEntity);
                    }
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ChannelManager.loadAllWhatsOnChannels() threw an exception", e);
        }
        return map;
    }

}
