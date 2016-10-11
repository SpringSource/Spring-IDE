/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.properties.editor.yaml.completions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.springframework.boot.configurationmetadata.ValueHint;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.properties.editor.FuzzyMap;
import org.springframework.ide.eclipse.boot.properties.editor.FuzzyMap.Match;
import org.springframework.ide.eclipse.boot.properties.editor.RelaxedNameConfig;
import org.springframework.ide.eclipse.boot.properties.editor.completions.JavaTypeNavigationHoverInfo;
import org.springframework.ide.eclipse.boot.properties.editor.completions.LazyProposalApplier;
import org.springframework.ide.eclipse.boot.properties.editor.completions.PropertyCompletionFactory;
import org.springframework.ide.eclipse.boot.properties.editor.completions.SpringPropertyHoverInfo;
import org.springframework.ide.eclipse.boot.properties.editor.completions.ValueHintHoverInfo;
import org.springframework.ide.eclipse.boot.properties.editor.metadata.HintProvider;
import org.springframework.ide.eclipse.boot.properties.editor.metadata.PropertyInfo;
import org.springframework.ide.eclipse.boot.properties.editor.metadata.StsValueHint;
import org.springframework.ide.eclipse.boot.properties.editor.util.Type;
import org.springframework.ide.eclipse.boot.properties.editor.util.TypeParser;
import org.springframework.ide.eclipse.boot.properties.editor.util.TypeUtil;
import org.springframework.ide.eclipse.boot.properties.editor.util.TypeUtil.BeanPropertyNameMode;
import org.springframework.ide.eclipse.boot.properties.editor.util.TypeUtil.EnumCaseMode;
import org.springframework.ide.eclipse.boot.properties.editor.util.TypedProperty;
import org.springframework.ide.eclipse.boot.properties.editor.yaml.reconcile.IndexNavigator;
import org.springframework.ide.eclipse.editor.support.completions.CompletionFactory.ScoreableProposal;
import org.springframework.ide.eclipse.editor.support.completions.DocumentEdits;
import org.springframework.ide.eclipse.editor.support.completions.ProposalApplier;
import org.springframework.ide.eclipse.editor.support.hover.HoverInfo;
import org.springframework.ide.eclipse.editor.support.util.CollectionUtil;
import org.springframework.ide.eclipse.editor.support.util.DocumentRegion;
import org.springframework.ide.eclipse.editor.support.util.FuzzyMatcher;
import org.springframework.ide.eclipse.editor.support.util.StringUtil;
import org.springframework.ide.eclipse.editor.support.util.YamlIndentUtil;
import org.springframework.ide.eclipse.editor.support.yaml.YamlDocument;
import org.springframework.ide.eclipse.editor.support.yaml.completions.AbstractYamlAssistContext;
import org.springframework.ide.eclipse.editor.support.yaml.completions.TopLevelAssistContext;
import org.springframework.ide.eclipse.editor.support.yaml.completions.YamlAssistContext;
import org.springframework.ide.eclipse.editor.support.yaml.completions.YamlPathEdits;
import org.springframework.ide.eclipse.editor.support.yaml.completions.YamlUtil;
import org.springframework.ide.eclipse.editor.support.yaml.path.YamlPath;
import org.springframework.ide.eclipse.editor.support.yaml.path.YamlPathSegment;
import org.springframework.ide.eclipse.editor.support.yaml.path.YamlPathSegment.YamlPathSegmentType;
import org.springframework.ide.eclipse.editor.support.yaml.structure.YamlStructureParser.SChildBearingNode;
import org.springframework.ide.eclipse.editor.support.yaml.structure.YamlStructureParser.SKeyNode;
import org.springframework.ide.eclipse.editor.support.yaml.structure.YamlStructureParser.SNode;

/**
 * Represents a context insied a "application.yml" file relative to which we can provide
 * content assistance.
 */
public abstract class ApplicationYamlAssistContext extends AbstractYamlAssistContext {

	protected final RelaxedNameConfig conf;

// This may prove useful later but we don't need it for now
	//	/**
	//	 * AssistContextKind is an classification of the different kinds of
	//	 * syntactic context that CA can be invoked from.
	//	 */
	//	public static enum Kind {
	//		SKEY_KEY, /* CA called from a SKeyNode and node.isInKey(cursor)==true */
	//		SKEY_VALUE, /* CA called from a SKeyNode and node.isInKey(cursor)==false */
	//		SRAW /* CA called from a SRawNode */
	//	}
	//	protected final Kind contextKind;

	public final TypeUtil typeUtil;

	public ApplicationYamlAssistContext(YamlDocument doc, int documentSelector, YamlPath contextPath, TypeUtil typeUtil, RelaxedNameConfig conf) {
		super(doc, documentSelector, contextPath);
		this.typeUtil = typeUtil;
		this.conf = conf;
	}

	/**
	 * Computes the text that should be appended at the end of a completion
	 * proposal depending on what type of value is expected.
	 */
	protected String appendTextFor(Type type) {
		//Note that proper indentation after each \n" is added automatically
		//so the strings created here do not need to contain indentation spaces.
		if (TypeUtil.isMap(type)) {
			//ready to enter nested map key on next line
			return "\n"+YamlIndentUtil.INDENT_STR;
		} if (TypeUtil.isSequencable(type)) {
			//ready to enter sequence element on next line
			return "\n- ";
		} else if (typeUtil.isAtomic(type)) {
			//ready to enter whatever on the same line
			return " ";
		} else {
			//Assume its some kind of pojo bean
			return "\n"+YamlIndentUtil.INDENT_STR;
		}
	}

	/**
	 * @return the type expected at this context, may return null if unknown.
	 */
	protected abstract Type getType();

	public static ApplicationYamlAssistContext subdocument(YamlDocument doc, int documentSelector, FuzzyMap<PropertyInfo> index, PropertyCompletionFactory completionFactory, TypeUtil typeUtil, RelaxedNameConfig conf) {
		return new IndexContext(doc, documentSelector, YamlPath.EMPTY, IndexNavigator.with(index), completionFactory, typeUtil, conf);
	}

	public static YamlAssistContext forPath(YamlDocument doc, YamlPath contextPath,  FuzzyMap<PropertyInfo> index, PropertyCompletionFactory completionFactory, TypeUtil typeUtil, RelaxedNameConfig conf) {
		try {
			YamlPathSegment documentSelector = contextPath.getSegment(0);
			if (documentSelector!=null) {
				contextPath = contextPath.dropFirst(1);
				YamlAssistContext context = ApplicationYamlAssistContext.subdocument(doc, documentSelector.toIndex(), index, completionFactory, typeUtil, conf);
				for (YamlPathSegment s : contextPath.getSegments()) {
					if (context==null) return null;
					context = context.traverse(s);
				}
				return context;
			}
		} catch (Exception e) {
			BootActivator.log(e);
		}
		return null;
	}

	@Override
	abstract public YamlAssistContext traverse(YamlPathSegment s) throws Exception;

	private static class TypeContext extends ApplicationYamlAssistContext {

		private PropertyCompletionFactory completionFactory;
		private Type type;
		private ApplicationYamlAssistContext parent;
		private HintProvider hints;

		public TypeContext(ApplicationYamlAssistContext parent, YamlPath contextPath, Type type,
				PropertyCompletionFactory completionFactory, TypeUtil typeUtil, RelaxedNameConfig conf, HintProvider hints) {
			super(parent.getDocument(), parent.documentSelector, contextPath, typeUtil, conf);
			this.parent = parent;
			this.completionFactory = completionFactory;
			this.type = type;
			this.hints = hints;
		}

		private HintProvider getHintProvider() {
			return hints;
		}

		@Override
		public Collection<ICompletionProposal> getCompletions(YamlDocument doc, SNode node, int offset) throws Exception {
			String query = getPrefix(doc, node, offset);
			EnumCaseMode enumCaseMode = enumCaseMode(query);
			BeanPropertyNameMode beanMode = conf.getBeanMode();
			List<ICompletionProposal> valueCompletions = getValueCompletions(doc, offset, query, enumCaseMode);
			if (!valueCompletions.isEmpty()) {
				return valueCompletions;
			}
			return getKeyCompletions(doc, offset, query, enumCaseMode, beanMode);
		}

		private EnumCaseMode enumCaseMode(String query) {
			if (query.isEmpty()) {
				return conf.getEnumMode();
			} else {
				return EnumCaseMode.ALIASED; // will match candidates from both lower and original based on what user typed
			}
		}

		public List<ICompletionProposal> getKeyCompletions(YamlDocument doc, int offset, String query,
				EnumCaseMode enumCaseMode, BeanPropertyNameMode beanMode) throws Exception {
			int queryOffset = offset - query.length();
			List<TypedProperty> properties = getProperties(query, enumCaseMode, beanMode);
			if (CollectionUtil.hasElements(properties)) {
				ArrayList<ICompletionProposal> proposals = new ArrayList<>(properties.size());
				SNode contextNode = getContextNode(doc);
				Set<String> definedProps = getDefinedProperties(contextNode);
				for (TypedProperty p : properties) {
					String name = p.getName();
					double score = FuzzyMatcher.matchScore(query, name);
					if (score!=0) {
						YamlPath relativePath = YamlPath.fromSimpleProperty(name);
						YamlPathEdits edits = new YamlPathEdits(doc);
						if (!definedProps.contains(name)) {
							//property not yet defined
							Type type = p.getType();
							edits.delete(queryOffset, query);
							edits.createPathInPlace(contextNode, relativePath, queryOffset, appendTextFor(type));
							proposals.add(completionFactory.beanProperty(doc.getDocument(),
									contextPath.toPropString(), getType(),
									query, p, score, edits, typeUtil)
							);
						} else {
							//property already defined
							// instead of filtering, navigate to the place where its defined.
							deleteQueryAndLine(doc, query, queryOffset, edits);
							//Cast to SChildBearingNode cannot fail because otherwise definedProps would be the empty set.
							edits.createPath((SChildBearingNode) contextNode, relativePath, "");
							proposals.add(
								completionFactory.beanProperty(doc.getDocument(),
									contextPath.toPropString(), getType(),
									query, p, score, edits, typeUtil)
								.deemphasize() //deemphasize because it already exists
							);
						}
					}
				}
				return proposals;
			}
			return Collections.emptyList();
		}

		protected List<TypedProperty> getProperties(String query, EnumCaseMode enumCaseMode, BeanPropertyNameMode beanMode) {
			ArrayList<TypedProperty> props = new ArrayList<>();
			List<TypedProperty> fromType = typeUtil.getProperties(type, enumCaseMode, beanMode);
			if (CollectionUtil.hasElements(fromType)) {
				props.addAll(fromType);
			}
			HintProvider hints = getHintProvider();
			if (hints!=null) {
				List<TypedProperty> fromHints = hints.getPropertyHints(query);
				if (CollectionUtil.hasElements(fromHints)) {
					props.addAll(fromHints);
				}
			}
			return props;
		}

		private Set<String> getDefinedProperties(SNode contextNode) {
			try {
				if (contextNode instanceof SChildBearingNode) {
					List<SNode> children = ((SChildBearingNode)contextNode).getChildren();
					if (CollectionUtil.hasElements(children)) {
						Set<String> keys = new HashSet<>(children.size());
						for (SNode c : children) {
							if (c instanceof SKeyNode) {
								keys.add(((SKeyNode) c).getKey());
							}
						}
						return keys;
					}
				}
			} catch (Exception e) {
				BootActivator.log(e);
			}
			return Collections.emptySet();
		}

		private List<ICompletionProposal> getValueCompletions(YamlDocument doc, int offset, String query, EnumCaseMode enumCaseMode) {
			Collection<StsValueHint> hints = getHintValues(query, doc, offset, enumCaseMode);
			if (hints!=null) {
				ArrayList<ICompletionProposal> completions = new ArrayList<>();
				for (StsValueHint hint : hints) {
					String value = hint.getValue();
					double score = FuzzyMatcher.matchScore(query, value);
					if (score!=0 && !value.equals(query)) {
						DocumentEdits edits = new DocumentEdits(doc.getDocument());
						int valueStart = offset-query.length();
						edits.delete(valueStart, offset);
						if (doc.getChar(valueStart-1)==':') {
							edits.insert(offset, " ");
						}
						edits.insert(offset, YamlUtil.stringEscape(value));
						completions.add(completionFactory.valueProposal(value, query, type, score, edits, new ValueHintHoverInfo(hint)));
					}
				}
				return completions;
			}
			return Collections.emptyList();
		}

		@Override
		public HoverInfo getValueHoverInfo(YamlDocument doc, DocumentRegion valueRegion) {
			String value = valueRegion.toString();

			if (TypeUtil.isClass(type)) {
				//Special case. We want hovers/hyperlinks even if the class is not a valid hint (as long as it is a class)
				StsValueHint hint = StsValueHint.className(value.toString(), typeUtil);
				if (hint!=null) {
					return new ValueHintHoverInfo(hint);
				}
			}

			Collection<StsValueHint> hints = getHintValues(value, doc, valueRegion.getEnd(), EnumCaseMode.ALIASED);
			//The hints where found by fuzzy match so they may not actually match exactly!
			for (StsValueHint h : hints) {
				if (value.equals(h.getValue())) {
					return new ValueHintHoverInfo(h);
				}
			}
			return super.getValueHoverInfo(doc, valueRegion);
		}

		protected Collection<StsValueHint> getHintValues(
				String query,
				YamlDocument doc, int offset,
				EnumCaseMode enumCaseMode
		) {
			Collection<StsValueHint> allHints = new ArrayList<>();
			{
				Collection<StsValueHint> hints = typeUtil.getHintValues(type, query, enumCaseMode);
				if (CollectionUtil.hasElements(hints)) {
					allHints.addAll(hints);
				}
			}
			{
				HintProvider hintProvider = getHintProvider();
				if (hintProvider!=null) {
					allHints.addAll(hintProvider.getValueHints(query));
				}
			}
			return allHints;
		}

		@Override
		public YamlAssistContext traverse(YamlPathSegment s) {
			if (s.getType()==YamlPathSegmentType.VAL_AT_KEY) {
				if (TypeUtil.isSequencable(type) || TypeUtil.isMap(type)) {
					return contextWith(s, TypeUtil.getDomainType(type));
				}
				String key = s.toPropString();
				Map<String, TypedProperty> subproperties = typeUtil.getPropertiesMap(type, EnumCaseMode.ALIASED, BeanPropertyNameMode.ALIASED);
				if (subproperties!=null) {
					return contextWith(s, TypedProperty.typeOf(subproperties.get(key)));
				}
			} else if (s.getType()==YamlPathSegmentType.VAL_AT_INDEX) {
				if (TypeUtil.isSequencable(type)) {
					return contextWith(s, TypeUtil.getDomainType(type));
				}
			}
			return null;
		}

		private AbstractYamlAssistContext contextWith(YamlPathSegment s, Type nextType) {
			if (nextType!=null) {
				return new TypeContext(this, contextPath.append(s), nextType, completionFactory, typeUtil, conf,
						new YamlPath(s).traverse(hints));
			}
			return null;
		}


		@Override
		public String toString() {
			return "TypeContext("+contextPath.toPropString()+"::"+type+")";
		}


		@Override
		public HoverInfo getHoverInfo() {
			if (parent instanceof IndexContext) {
				//this context is in fact an 'alias' of its parent, representing the
				// point in the context hierarchy where a we transition from navigating
				// the index to navigating type/bean properties
				return parent.getHoverInfo();
			} else {
				return new JavaTypeNavigationHoverInfo(contextPath.toPropString(), contextPath.getBeanPropertyName(), parent.getType(), getType(), typeUtil);
			}
		}

		@Override
		protected Type getType() {
			return type;
		}

	}

	private static class IndexContext extends ApplicationYamlAssistContext {

		private IndexNavigator indexNav;
		PropertyCompletionFactory completionFactory;

		public IndexContext(YamlDocument doc, int documentSelector, YamlPath contextPath, IndexNavigator indexNav,
				PropertyCompletionFactory completionFactory, TypeUtil typeUtil, RelaxedNameConfig conf) {
			super(doc, documentSelector, contextPath, typeUtil, conf);
			this.indexNav = indexNav;
			this.completionFactory = completionFactory;
		}

		@Override
		public Collection<ICompletionProposal> getCompletions(YamlDocument doc, SNode node, int offset) throws Exception {
			String query = getPrefix(doc, node, offset);
			Collection<Match<PropertyInfo>> matchingProps = indexNav.findMatching(query);
			if (!matchingProps.isEmpty()) {
				ArrayList<ICompletionProposal> completions = new ArrayList<>();
				for (Match<PropertyInfo> match : matchingProps) {
					ProposalApplier edits = createEdits(doc, offset, query, match);
					ScoreableProposal completion = completionFactory.property(
							doc.getDocument(), edits, match, typeUtil
					);
					if (getContextRoot(doc).exists(YamlPath.fromProperty(match.data.getId()))) {
						completion.deemphasize();
					}
					completions.add(completion);
				}
				return completions;
			}
			return Collections.emptyList();
		}

		protected ProposalApplier createEdits(final YamlDocument file,
				final int offset, final String query, final Match<PropertyInfo> match)
				throws Exception {
			//Edits created lazyly as they are somwehat expensive to compute and mostly
			// we need only the edits for the one proposal that user picks.
			return new LazyProposalApplier() {
				@Override
				protected ProposalApplier create() throws Exception {
					YamlPathEdits edits = new YamlPathEdits(file);

					int queryOffset = offset-query.length();
					edits.delete(queryOffset, query);

					YamlPath propertyPath = YamlPath.fromProperty(match.data.getId());
					YamlPath relativePath = propertyPath.dropFirst(contextPath.size());
					YamlPathSegment nextSegment = relativePath.getSegment(0);
					SNode contextNode = getContextNode(file);
					//To determine if this completion is 'in place' or needs to be inserted
					// elsewhere in the tree, we check whether a node already exists in our
					// context. If it doesn't we can create it as any child of the context
					// so that includes, right at place the user is typing now.
					SNode existingNode = contextNode.traverse(nextSegment);
					String appendText = appendTextFor(TypeParser.parse(match.data.getType()));
					if (existingNode==null) {
						edits.createPathInPlace(contextNode, relativePath, queryOffset, appendText);
					} else {
						String wholeLine = file.getLineTextAtOffset(queryOffset);
						if (wholeLine.trim().equals(query.trim())) {
							edits.deleteLineBackwardAtOffset(queryOffset);
						}
						edits.createPath(getContextRoot(file), YamlPath.fromProperty(match.data.getId()), appendText);
					}
					return edits;
				}
			};
		}

		@Override
		public AbstractYamlAssistContext traverse(YamlPathSegment s) {
			if (s.getType()==YamlPathSegmentType.VAL_AT_KEY) {
				String key = s.toPropString();
				IndexNavigator subIndex = indexNav.selectSubProperty(key);
				if (subIndex.isEmpty()) {
					//Nothing found for actual key... maybe its a 'camelCased' alias of real key?
					String keyAlias = StringUtil.camelCaseToHyphens(key);
					if (!keyAlias.equals(key)) { // no point checking alias is the same (likely key was not camelCased)
						IndexNavigator aliasedSubIndex = indexNav.selectSubProperty(keyAlias);
						if (!aliasedSubIndex.isEmpty()) {
							subIndex = aliasedSubIndex;
						}
					}
				}
				if (subIndex.getExtensionCandidate()!=null) {
					return new IndexContext(getDocument(), documentSelector, contextPath.append(s), subIndex, completionFactory, typeUtil, conf);
				} else if (subIndex.getExactMatch()!=null) {
					IndexContext asIndexContext = new IndexContext(getDocument(), documentSelector, contextPath.append(s), subIndex, completionFactory, typeUtil, conf);
					PropertyInfo prop = subIndex.getExactMatch();
					return new TypeContext(asIndexContext, contextPath.append(s), TypeParser.parse(prop.getType()), completionFactory, typeUtil, conf, prop.getHints(typeUtil, true));
				}
			}
			//Unsuported navigation => no context for assist
			return null;
		}

		@Override
		public String toString() {
			return "YamlAssistIndexContext("+indexNav+")";
		}

		@Override
		protected Type getType() {
			PropertyInfo match = indexNav.getExactMatch();
			if (match!=null) {
				return TypeParser.parse(match.getType());
			}
			return null;
		}

		@Override
		public HoverInfo getHoverInfo() {
			PropertyInfo prop = indexNav.getExactMatch();
			if (prop!=null) {
				return new SpringPropertyHoverInfo(typeUtil.getJavaProject(), prop);
			}
			return null;
		}
	}

	public abstract HoverInfo getHoverInfo();
	public HoverInfo getHoverInfo(YamlPathSegment s) {
		//ApplicationYamlAssistContext implements getHoverInfo directly. so this is not needed.
		return null;
	}

	public static YamlAssistContext global(YamlDocument doc, final FuzzyMap<PropertyInfo> index, final PropertyCompletionFactory completionFactory, final TypeUtil typeUtil, final RelaxedNameConfig conf) {
		return new TopLevelAssistContext() {
			@Override
			protected YamlAssistContext getDocumentContext(int documentSelector) {
				return subdocument(doc, documentSelector, index, completionFactory, typeUtil, conf);
			}

			@Override
			public YamlDocument getDocument() {
				return doc;
			}
		};
	}
}
