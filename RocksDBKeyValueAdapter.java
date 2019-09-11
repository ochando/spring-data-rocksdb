import java.io.File;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.SerializationUtils;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.keyvalue.core.AbstractKeyValueAdapter;
import org.springframework.data.keyvalue.core.ForwardingCloseableIterator;
import org.springframework.data.util.CloseableIterator;
import org.springframework.util.Assert;

import com.google.common.collect.Iterators;

public class RocksDBKeyValueAdapter extends AbstractKeyValueAdapter {

    private static final String CLASS_NAME = RocksDBKeyValueAdapter.class.getName();
    private static final Logger log = LoggerFactory.getLogger(CLASS_NAME);

    @Value("${repository.path}")
    private String repositoryPath;

    private final Map<String, RocksDB> store;

    public RocksDBKeyValueAdapter() {
	RocksDB.loadLibrary();

	store = new ConcurrentHashMap<>(10);
    }

    /* (non-Javadoc)
     * @see org.springframework.data.keyvalue.core.KeyValueAdapter#put(java.lang.Object, java.lang.Object, java.lang.String)
     */
    @Override
    public Object put(Object id, Object item, String keyspace) {
	try {
	    this.getKeySpaceDB(keyspace).put(toBytes(id), toBytes(item));
	} catch (RocksDBException e) {
	    log.error(MessageFormat.format("Fail to put registry with key: {0}", id), e);
	}
	return item;
    }

    /* (non-Javadoc)
     * @see org.springframework.data.keyvalue.core.KeyValueAdapter#contains(java.lang.Object, java.lang.String)
     */
    @Override
    public boolean contains(Object id, String keyspace) {
	RocksIterator iterator = this.getKeySpaceDB(keyspace).newIterator();
	boolean contains = false;
	for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
	    if (id.equals(iterator.key())) {
		contains = true;
		break;
	    }
	}
	iterator.close();
	return contains;
    }

    /* (non-Javadoc)
     * @see org.springframework.data.keyvalue.core.KeyValueAdapter#get(java.lang.Object, java.lang.String)
     */
    @Override
    public Object get(Object id, String keyspace) {
	byte[] value = null;
	try {
	    value = this.getKeySpaceDB(keyspace).get(toBytes(id));
	} catch (RocksDBException e) {
	    log.error(MessageFormat.format("Fail to get registry with key: {0}", id), e);
	}
	return value != null ? fromBytes(value) : null;
    }

    /* (non-Javadoc)
     * @see org.springframework.data.keyvalue.core.KeyValueAdapter#delete(java.lang.Object, java.lang.String)
     */
    @Override
    public Object delete(Object id, String keyspace) {
	Object object = this.get(id, keyspace);
	try {
	    if (object != null) {
		this.getKeySpaceDB(keyspace).delete(toBytes(id));
	    }
	} catch (RocksDBException e) {
	    log.error(MessageFormat.format("Fail to delete registry with key: {0}", id), e);
	}
	return object;
    }

    /* (non-Javadoc)
     * @see org.springframework.data.keyvalue.core.KeyValueAdapter#getAllOf(java.lang.String)
     */
    @Override
    public Iterable<?> getAllOf(String keyspace) {
	RocksIterator iterator = this.getKeySpaceDB(keyspace).newIterator();
	ArrayList<Object> values = new ArrayList<>();
	for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
	    byte[] bytes = iterator.value();
	    if (bytes != null && bytes.length > 0) {
		values.add(fromBytes(bytes));
	    }
	}
	iterator.close();
	return values;
    }

    /* (non-Javadoc)
     * @see org.springframework.data.keyvalue.core.KeyValueAdapter#entries(java.lang.String)
     */
    @Override
    public CloseableIterator<Entry<Object, Object>> entries(String keyspace) {
	RocksIterator iterator = this.getKeySpaceDB(keyspace).newIterator();
	ArrayList<Entry<Object, Object>> items = new ArrayList<>();
	for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
	    items.add(new AbstractMap.SimpleEntry<>(iterator.key(), iterator.value()));
	}
	iterator.close();
	return new ForwardingCloseableIterator<>(items.iterator());
    }

    /* (non-Javadoc)
     * @see org.springframework.data.keyvalue.core.KeyValueAdapter#deleteAllOf(java.lang.String)
     */
    @Override
    public void deleteAllOf(String keyspace) {
	RocksDB db = this.getKeySpaceDB(keyspace);
	RocksIterator iterator = db.newIterator();

	for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
	    byte[] id = iterator.key();
	    try {
		db.delete(id);
	    } catch (RocksDBException e) {
		log.error(MessageFormat.format("Fail to delete registry with key: {0}", id), e);
	    }
	}
	iterator.close();
    }

    /* (non-Javadoc)
     * @see org.springframework.data.keyvalue.core.KeyValueAdapter#clear()
     */
    @Override
    public void clear() {
	this.store.values().stream().filter(Objects::nonNull).forEach(RocksDB::close);
	this.store.clear();
    }

    /* (non-Javadoc)
     * @see org.springframework.data.keyvalue.core.KeyValueAdapter#count(java.lang.String)
     */
    @Override
    public long count(String keyspace) {
	return Iterators.size(this.getAllOf(keyspace).iterator());
    }

    /* (non-Javadoc)
     * @see org.springframework.beans.factory.DisposableBean#destroy()
     */
    @Override
    public void destroy() throws Exception {
	this.store.values().stream().filter(Objects::nonNull).forEach(db -> {
	    try {
		RocksDB.destroyDB(db.getName(), null);
	    } catch (RocksDBException e) {
		log.error("A problem ocurred while destroying DB.", e);
	    }
	});
    }

    /**
     * Get DB associated with given key space.
     *
     * @param keyspace must not be {@literal null}.
     * @return
     */
    protected RocksDB getKeySpaceDB(String keyspace) {

	Assert.notNull(keyspace, "Collection must not be null for lookup.");

	RocksDB db = this.store.get(keyspace);

	if (db != null) {
	    return db;
	}

	addDBForKeySpace(keyspace);
	return this.store.get(keyspace);
    }

    private void addDBForKeySpace(String keyspace) {
	Options options = new Options();
	options.setCreateIfMissing(true);

	try {
	    RocksDB rocksDB = RocksDB.open(options, repositoryPath + File.separator + "rocksDBStore_" + keyspace);
	    this.store.put(keyspace, rocksDB);

	} catch (RocksDBException e) {
	    log.error("A problem ocurred while opening DB repository storage.", e);
	    System.exit(1);
	}
    }

    private Object fromBytes(byte[] bytes) {
	return SerializationUtils.deserialize(bytes);
    }

    private byte[] toBytes(Object object) {
	return SerializationUtils.serialize((Serializable) object);
    }
}
