/*******************************************************************************
 * Copyright (c) 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.yaml.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.inject.Provider;

import org.eclipse.core.runtime.Assert;
import org.springframework.ide.eclipse.editor.support.hover.DescriptionProviders;
import org.springframework.ide.eclipse.editor.support.util.EnumValueParser;
import org.springframework.ide.eclipse.editor.support.util.HtmlSnippet;
import org.springframework.ide.eclipse.editor.support.util.ValueParser;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;

/**
 * Static utility method for creating YType objects representing either
 * 'array-like', 'map-like' or 'object-like' types which can be used
 * to build up a 'Yaml Schema'.
 *
 * @author Kris De Volder
 */
public class YTypeFactory {

	public YType yany(String name) {
		return new YAny(name);
	}

	public YType yseq(YType el) {
		return new YSeqType(el);
	}

	public YType ymap(YType key, YType val) {
		return new YMapType(key, val);
	}

	public YBeanType ybean(String name, YTypedProperty... properties) {
		return new YBeanType(name, properties);
	}

	public YBeanUnionType yunion(String name, YBeanType... types) {
		Assert.isLegal(types.length>0);
		return new YBeanUnionType(name, types);
	}

	/**
	 * YTypeUtil instances capable of 'interpreting' the YType objects created by this
	 * YTypeFactory
	 */
	public final YTypeUtil TYPE_UTIL = new YTypeUtil() {

		@Override
		public boolean isSequencable(YType type) {
			return ((AbstractType)type).isSequenceable();
		}

		@Override
		public boolean isMap(YType type) {
			return ((AbstractType)type).isMap();
		}

		@Override
		public boolean isAtomic(YType type) {
			return ((AbstractType)type).isAtomic();
		}

		@Override
		public Map<String, YTypedProperty> getPropertiesMap(YType type, DynamicSchemaContext dc) {
			return ((AbstractType)type).getPropertiesMap(dc);
		}

		@Override
		public List<YTypedProperty> getProperties(YType type, DynamicSchemaContext dc) {
			return ((AbstractType)type).getProperties(dc);
		}

		@Override
		public YValueHint[] getHintValues(YType type) {
			return ((AbstractType)type).getHintValues();
		}

		@Override
		public YType getDomainType(YType type) {
			return ((AbstractType)type).getDomainType();
		}

		@Override
		public String niceTypeName(YType type) {
			return type.toString();
		}

		@Override
		public YType getKeyType(YType type) {
			return ((AbstractType)type).getKeyType();
		}

		@Override
		public boolean isBean(YType type) {
			return ((AbstractType)type).isBean();
		}

		@Override
		public ValueParser getValueParser(YType type) {
			return ((AbstractType)type).getParser();
		}
	};

	/////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Provides default implementations for all YType methods this YTypeFactory's objects care about.
	 */
	public static abstract class AbstractType implements YType {

		private ValueParser parser;
		private List<YTypedProperty> propertyList = new ArrayList<>();
		private final List<YValueHint> hints = new ArrayList<>();
		private Map<String, YTypedProperty> cachedPropertyMap;
		private Provider<Collection<YValueHint>> hintProvider;

		public boolean isSequenceable() {
			return false;
		}

		public boolean isBean() {
			return false;
		}

		public YType getKeyType() {
			return null;
		}

		public YType getDomainType() {
			return null;
		}

		public void addHintProvider(Provider<Collection<YValueHint>> hintProvider) {
			this.hintProvider = hintProvider;
		}

		public YValueHint[] getHintValues() {
			Collection<YValueHint> providerHints = hintProvider != null ? hintProvider.get() : null;

			if (providerHints == null || providerHints.isEmpty()) {
				return hints.toArray(new YValueHint[hints.size()]);
			} else {
				// Only merge if there are provider hints to merge
				Set<YValueHint> mergedHints = new LinkedHashSet<>();

				// Add type hints first
				for (YValueHint val : hints) {
					mergedHints.add(val);
				}

				// merge the provider hints
				for (YValueHint val : providerHints) {
					mergedHints.add(val);
				}
				return mergedHints.toArray(new YValueHint[mergedHints.size()]);
			}
		}

		public List<YTypedProperty> getProperties(DynamicSchemaContext dc) {
			return Collections.unmodifiableList(propertyList);
		}

		public Map<String, YTypedProperty> getPropertiesMap(DynamicSchemaContext dc) {
			if (cachedPropertyMap==null) {
				cachedPropertyMap = new LinkedHashMap<>();
				for (YTypedProperty p : propertyList) {
					cachedPropertyMap.put(p.getName(), p);
				}
			}
			return Collections.unmodifiableMap(cachedPropertyMap);
		}

		public boolean isAtomic() {
			return false;
		}

		public boolean isMap() {
			return false;
		}

		public abstract String toString(); // force each sublcass to implement a (nice) toString method.

		public void addProperty(YTypedProperty p) {
			cachedPropertyMap = null;
			propertyList.add(p);
		}

		public void addProperty(String name, YType type, Provider<HtmlSnippet> description) {
			YTypedPropertyImpl prop;
			addProperty(prop = new YTypedPropertyImpl(name, type));
			prop.setDescriptionProvider(description);
		}

		public void addProperty(String name, YType type) {
			addProperty(new YTypedPropertyImpl(name, type));
		}
		public void addHints(String... strings) {
			if (strings != null) {
				for (String value : strings) {
					BasicYValueHint hint = new BasicYValueHint(value);
					if (!hints.contains(hint)) {
						hints.add(hint);
					}
				}
			}
		}

		public void addHints(YValueHint... extraHints) {
			for (YValueHint h : extraHints) {
				if (!hints.contains(h)) {
					hints.add(h);
				}
			}
		}

		public void parseWith(ValueParser parser) {
			this.parser = parser;
		}
		public ValueParser getParser() {
			return parser;
		}
	}

	/**
	 * Represents a type that is completly uncontrained. Anyhting goes. A map, a sequence or some
	 * atomic value.
	 */
	public class YAny extends AbstractType {
		private final String name;

		public YAny(String name) {
			this.name = name;
		}

		@Override
		public boolean isAtomic() {
			return true;
		}

		@Override
		public boolean isSequenceable() {
			return true;
		}

		@Override
		public boolean isMap() {
			return true;
		}

		@Override
		public String toString() {
			return name;
		}

	}

	public static class YMapType extends AbstractType {

		private final YType key;
		private final YType val;

		private YMapType(YType key, YType val) {
			this.key = key;
			this.val = val;
		}

		@Override
		public String toString() {
			return "Map<"+key.toString()+", "+(val==null?"null":val.toString())+">";
		}

		@Override
		public boolean isMap() {
			return true;
		}

		@Override
		public YType getKeyType() {
			return key;
		}

		@Override
		public YType getDomainType() {
			return val;
		}
	}

	public static class YSeqType extends AbstractType {

		private YType el;

		private YSeqType(YType el) {
			this.el = el;
		}

		@Override
		public String toString() {
			return el.toString()+"[]";
		}

		@Override
		public boolean isSequenceable() {
			return true;
		}

		@Override
		public YType getDomainType() {
			return el;
		}
	}

	public static class YBeanType extends AbstractType {
		private final String name;

		public YBeanType(String name, YTypedProperty... properties) {
			this.name = name;
			for (YTypedProperty p : properties) {
				addProperty(p);
			}
		}

		@Override
		public String toString() {
			return name;
		}

		public boolean isBean() {
			return true;
		}
	}

	/**
	 * Represents a union of several bean types. It is assumed one primary property
	 * exists in each of the the sub-bean types that can be used to identify the
	 * type. In other words the primary property has a unique name so that when
	 * this property is being assigned a value we can immediatly infer from that
	 * fact of the specifc sub-bean type we are dealing with.
	 */
	public class YBeanUnionType extends AbstractType {
		private final String name;

		private Map<String, AbstractType> typesByPrimary = new HashMap<>();

		private ImmutableList<YTypedProperty> primaryProps;

		public YBeanUnionType(String name, YBeanType... types) {
			this.name = name;
			for (YType _t : types) {
				AbstractType t = (AbstractType)_t;
				typesByPrimary.put(findPrimary(t, types), t);
			}
		}
		private String findPrimary(AbstractType t, YBeanType[] types) {
			//Note: passing null dynamic context below is okay, assuming the properties in YBeanType
			// do not care about dynamic context.
			for (YTypedProperty p : t.getProperties(null)) {
				String name = p.getName();
				if (isUniqueFor(name, t, types)) {
					return name;
				}
			}
			Assert.isLegal(false, "Couldn't find a unique property key for "+t);
			return null; //unreachable, but compiler doesn't know.
		}
		private boolean isUniqueFor(String name, AbstractType t, YBeanType[] types) {
			for (YBeanType other : types) {
				if (other!=t) {
					//Note: passing null dynamic context below is okay, assuming the properties in YBeanType
					// do not care about dynamic context.
					if (other.getPropertiesMap(null).containsKey(name)) {
						return false;
					}
				}
			}
			return true;
		}
		@Override
		public String toString() {
			return name;
		}
		@Override
		public boolean isBean() {
			return true;
		}

		@Override
		public Map<String, YTypedProperty> getPropertiesMap(DynamicSchemaContext dc) {
			return asMap(getProperties(dc));
		}

		private Map<String, YTypedProperty> asMap(List<YTypedProperty> properties) {
			ImmutableMap.Builder<String, YTypedProperty> builder = ImmutableMap.builder();
			for (YTypedProperty p : properties) {
				builder.put(p.getName(), p);
			}
			return builder.build();
		}

		@Override
		public List<YTypedProperty> getProperties(DynamicSchemaContext dc) {
			Set<String> existingProps = dc.getDefinedProperties();
			if (!existingProps.isEmpty()) {
				for (Entry<String, AbstractType> entry : typesByPrimary.entrySet()) {
					String primaryName = entry.getKey();
					if (existingProps.contains(primaryName)) {
						return entry.getValue().getProperties(dc);
					}
				}
			}
			//Reaching here means we couldn't guess the type from existing props.
			//We'll just return the primary properties, these are good to give as hints
			//then, since at least one of them should typically be added.
			return getPrimaryProps(dc);
		}

		private List<YTypedProperty> getPrimaryProps(DynamicSchemaContext dc) {
			if (primaryProps==null) {
				Builder<YTypedProperty> builder = ImmutableList.builder();
				for (Entry<String, AbstractType> entry : typesByPrimary.entrySet()) {
					builder.add(entry.getValue().getPropertiesMap(dc).get(entry.getKey()));
				}
				primaryProps = builder.build();
			}
			return primaryProps;
		}
	}

	public static class YAtomicType extends AbstractType {
		private final String name;
		private YAtomicType(String name) {
			this.name = name;
		}
		@Override
		public String toString() {
			return name;
		}
		@Override
		public boolean isAtomic() {
			return true;
		}
	}

	public static class YTypedPropertyImpl implements YTypedProperty {

		final private String name;
		final private YType type;
		private Provider<HtmlSnippet> descriptionProvider = DescriptionProviders.NO_DESCRIPTION;

		private YTypedPropertyImpl(String name, YType type) {
			this.name = name;
			this.type = type;
		}

		@Override
		public String getName() {
			return this.name;
		}

		@Override
		public YType getType() {
			return this.type;
		}

		@Override
		public String toString() {
			return name + ":" + type;
		}

		@Override
		public HtmlSnippet getDescription() {
			return descriptionProvider.get();
		}

		public void setDescriptionProvider(Provider<HtmlSnippet> descriptionProvider) {
			this.descriptionProvider = descriptionProvider;
		}

	}

	public YAtomicType yatomic(String name) {
		return new YAtomicType(name);
	}

	public YTypedPropertyImpl yprop(String name, YType type) {
		return new YTypedPropertyImpl(name, type);
	}

	public YAtomicType yenum(String name, String... values) {
		YAtomicType t = yatomic(name);
		t.addHints(values);
		t.parseWith(new EnumValueParser(name, values));
		return t;
	}

	public YValueHint hint(String value, String label) {
		return new BasicYValueHint(value, label);
	}

}
