package com.aerospike.mapper.tools.mappers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.aerospike.client.AerospikeException;
import com.aerospike.mapper.annotations.AerospikeEmbed.EmbedType;
import com.aerospike.mapper.tools.AeroMapper;
import com.aerospike.mapper.tools.ClassCache;
import com.aerospike.mapper.tools.ClassCacheEntry;
import com.aerospike.mapper.tools.TypeMapper;
import com.aerospike.mapper.tools.TypeUtils;
import com.aerospike.mapper.tools.TypeUtils.AnnotatedType;

public class ListMapper extends TypeMapper {

	@SuppressWarnings("unused")
	private final Class<?> referencedClass;
	private final Class<?> instanceClass;
	private final AeroMapper mapper;
	private final boolean supportedWithoutTranslation;
	private final TypeMapper instanceClassMapper;
	private final EmbedType embedType;
	private final ClassCacheEntry<?> subTypeEntry;
	private final boolean saveKey;
	
	public ListMapper(final Class<?> clazz, final Class<?> instanceClass, final TypeMapper instanceClassMapper, final AeroMapper mapper, final EmbedType embedType, final boolean saveKey) {
		this.referencedClass = clazz;
		this.mapper = mapper;
		this.instanceClass = instanceClass;
		this.supportedWithoutTranslation = TypeUtils.isAerospikeNativeType(instanceClass);
		this.instanceClassMapper = instanceClassMapper;
		this.saveKey = saveKey;
		
		if (embedType == EmbedType.DEFAULT) {
			this.embedType = EmbedType.LIST;
		}
		else {
			this.embedType = embedType;
		}
		if (this.embedType == EmbedType.MAP && (instanceClassMapper == null || (!ObjectMapper.class.isAssignableFrom(instanceClassMapper.getClass())))) {
			subTypeEntry = null;
			// TODO: Should this throw an exception or just change the embedType back to LIST?
			throw new AerospikeException("Annotations embedding lists of objects can only map those objects to maps instead of lists if the object is an AerospikeRecord on instance of class " + clazz.getSimpleName());
		}
		else {
			if (instanceClass != null) {
				subTypeEntry = ClassCache.getInstance().loadClass(instanceClass, mapper);
			}
			else {
				subTypeEntry = null;
			}
		}
	}
	
	@Override
	public Object toAerospikeFormat(Object value) {
		if (value == null) {
			return null;
		}
		List<?> list = (List<?>)value;
		if (list.size() == 0 || this.supportedWithoutTranslation) {
			return value;
		}
		if (embedType == null || embedType == EmbedType.LIST) {
			List<Object> results = new ArrayList<>();
			if (instanceClass == null) {
				// We don't have any hints as to how to translate them, we have to look up each type
				for (Object obj : list) {
					if (obj == null) {
						results.add(null);
					}
					else {
						TypeMapper thisMapper = TypeUtils.getMapper(obj.getClass(), null, mapper);
						results.add(thisMapper == null ? obj : thisMapper.toAerospikeFormat(obj, false));
					}
				}
			}
			else {
				for (Object obj : list) {
					if (obj == null || obj.getClass().equals(instanceClass)) {
						results.add(this.instanceClassMapper.toAerospikeFormat(obj));
					}
					else {
						// This class must be a subclass of the annotated type
						results.add(this.instanceClassMapper.toAerospikeFormat(obj, false));
					}
				}
			}
			return results;
		}
		else {
			Map<Object, Object> results = new TreeMap<>();
			for (Object obj : list) {
				Object key = subTypeEntry.getKey(obj);
				Object item;
				if (obj == null || obj.getClass().equals(instanceClass)) {
					item = this.instanceClassMapper.toAerospikeFormat(obj);
				}
				else {
					// This class must be a subclass of the annotated type
					item = this.instanceClassMapper.toAerospikeFormat(obj, false);
				}
				results.put(key, item);
			}
			return results;

		}
	}

	@Override
	public Object fromAerospikeFormat(Object value) {
		if (value == null) {
			return null;
		}
		List<Object> results = new ArrayList<>();
		if (embedType == EmbedType.DEFAULT || embedType == EmbedType.LIST) {
			List<?> list = (List<?>)value;
			if (list.size() == 0 || this.supportedWithoutTranslation) {
				return value;
			}

			if (instanceClass == null) {
				// We don't have any hints as to how to translate them, we have to look up each type
				for (Object obj : list) {
					if (obj == null) {
						results.add(null);
					}
					else {
						TypeMapper thisMapper = TypeUtils.getMapper(obj.getClass(), AnnotatedType.getDefaultAnnotateType(), mapper);
						results.add(thisMapper == null ? obj : thisMapper.fromAerospikeFormat(obj));
					}
				}
			}
			else {
				for (Object obj : list) {
					results.add(this.instanceClassMapper.fromAerospikeFormat(obj));
				}
			}
		}
		else {
			Map<?, ?> map = (Map<?,?>)value;
			for (Object key : map.keySet()) {
				Object item = map.get(key);
				
				Object result = this.instanceClassMapper.fromAerospikeFormat(item);
				subTypeEntry.setKey(result, key);
				results.add(result);
			}
		}
		return results;
	}
}
