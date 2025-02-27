/*
 * Copyright 2019-2025 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file incorporates code covered by the following terms:
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.algebra.metadata;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.algebra.AlgCollation;
import org.polypheny.db.algebra.AlgDistribution;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.constant.ExplainLevel;
import org.polypheny.db.algebra.metadata.BuiltInMetadata.TupleCount;
import org.polypheny.db.catalog.entity.Entity;
import org.polypheny.db.plan.AlgOptCost;
import org.polypheny.db.plan.AlgOptPredicateList;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.rex.RexTableIndexRef.AlgTableRef;
import org.polypheny.db.util.ImmutableBitSet;


/**
 * AlgMetadataQuery provides a strongly-typed facade on top of {@link AlgMetadataProvider} for the set of algebra expression metadata queries defined as standard within Polypheny-DB.
 * The Javadoc on these methods serves as their primary specification.
 * <p>
 * To add a new standard query <code>Xyz</code> to this interface, follow these steps:
 *
 * <ol>
 * <li>Add a static method <code>getXyz</code> specification to this class.</li>
 * <li>Add unit tests to {@link AlgMetadataProvider}.</li>
 * <li>Write a new provider class <code>RelMdXyz</code> in this package. Follow the pattern from an existing class such as {@link AlgMdColumnOrigins}, overloading on all of the logical algebra expressions to which the query applies.</li>
 * <li>Add a {@code SOURCE} static member, similar to {@link AlgMdColumnOrigins#SOURCE}.</li>
 * <li>Register the {@code SOURCE} object in {@link DefaultAlgMetadataProvider}.</li>
 * <li>Get unit tests working.
 * </ol>
 *
 * Because algebra expression metadata is extensible, extension projects can define similar facades in order to specify access to custom metadata. Please do not add queries here (nor on {@link AlgNode}) which lack meaning
 * outside of your extension.
 * <p>
 * Besides adding new metadata queries, extension projects may need to add custom providers for the standard queries in order to handle additional algebra expressions (either logical or physical). In either case, the
 * process is the same: write a reflective provider and chain it on to an instance of {@link DefaultAlgMetadataProvider}, pre-pending it to the default providers. Then supply that instance to the planner via the appropriate
 * plugin mechanism.
 */
@Slf4j
public class AlgMetadataQuery {

    /**
     * Set of active metadata queries, and cache of previous results.
     */
    public final Map<List<?>, Object> map = new HashMap<>();

    public final JaninoRelMetadataProvider metadataProvider;

    protected static final AlgMetadataQuery EMPTY = new AlgMetadataQuery( false );

    private BuiltInMetadata.Collation.Handler collationHandler;
    private BuiltInMetadata.ColumnOrigin.Handler columnOriginHandler;
    private BuiltInMetadata.ExpressionLineage.Handler expressionLineageHandler;
    private BuiltInMetadata.TableReferences.Handler tableReferencesHandler;
    private BuiltInMetadata.ColumnUniqueness.Handler columnUniquenessHandler;
    private BuiltInMetadata.CumulativeCost.Handler cumulativeCostHandler;
    private BuiltInMetadata.DistinctRowCount.Handler distinctRowCountHandler;
    private BuiltInMetadata.Distribution.Handler distributionHandler;
    private BuiltInMetadata.ExplainVisibility.Handler explainVisibilityHandler;
    private BuiltInMetadata.MaxRowCount.Handler maxRowCountHandler;
    private BuiltInMetadata.MinRowCount.Handler minRowCountHandler;
    private BuiltInMetadata.Memory.Handler memoryHandler;
    private BuiltInMetadata.NonCumulativeCost.Handler nonCumulativeCostHandler;
    private BuiltInMetadata.Parallelism.Handler parallelismHandler;
    private BuiltInMetadata.PercentageOriginalRows.Handler percentageOriginalRowsHandler;
    private BuiltInMetadata.PopulationSize.Handler populationSizeHandler;
    private BuiltInMetadata.Predicates.Handler predicatesHandler;
    private BuiltInMetadata.AllPredicates.Handler allPredicatesHandler;
    private BuiltInMetadata.NodeTypes.Handler nodeTypesHandler;
    private TupleCount.Handler rowCountHandler;
    private BuiltInMetadata.Selectivity.Handler selectivityHandler;
    private BuiltInMetadata.Size.Handler sizeHandler;
    private BuiltInMetadata.UniqueKeys.Handler uniqueKeysHandler;

    public static final ThreadLocal<JaninoRelMetadataProvider> THREAD_PROVIDERS = ThreadLocal.withInitial( () -> JaninoRelMetadataProvider.DEFAULT );


    protected AlgMetadataQuery( JaninoRelMetadataProvider metadataProvider, AlgMetadataQuery prototype ) {
        this.metadataProvider = Objects.requireNonNull( metadataProvider );
        this.collationHandler = prototype.collationHandler;
        this.columnOriginHandler = prototype.columnOriginHandler;
        this.expressionLineageHandler = prototype.expressionLineageHandler;
        this.tableReferencesHandler = prototype.tableReferencesHandler;
        this.columnUniquenessHandler = prototype.columnUniquenessHandler;
        this.cumulativeCostHandler = prototype.cumulativeCostHandler;
        this.distinctRowCountHandler = prototype.distinctRowCountHandler;
        this.distributionHandler = prototype.distributionHandler;
        this.explainVisibilityHandler = prototype.explainVisibilityHandler;
        this.maxRowCountHandler = prototype.maxRowCountHandler;
        this.minRowCountHandler = prototype.minRowCountHandler;
        this.memoryHandler = prototype.memoryHandler;
        this.nonCumulativeCostHandler = prototype.nonCumulativeCostHandler;
        this.parallelismHandler = prototype.parallelismHandler;
        this.percentageOriginalRowsHandler = prototype.percentageOriginalRowsHandler;
        this.populationSizeHandler = prototype.populationSizeHandler;
        this.predicatesHandler = prototype.predicatesHandler;
        this.allPredicatesHandler = prototype.allPredicatesHandler;
        this.nodeTypesHandler = prototype.nodeTypesHandler;
        this.rowCountHandler = prototype.rowCountHandler;
        this.selectivityHandler = prototype.selectivityHandler;
        this.sizeHandler = prototype.sizeHandler;
        this.uniqueKeysHandler = prototype.uniqueKeysHandler;
    }


    protected static <H> H initialHandler( Class<H> handlerClass ) {
        return handlerClass.cast(
                Proxy.newProxyInstance( AlgMetadataQuery.class.getClassLoader(),
                        new Class[]{ handlerClass }, ( proxy, method, args ) -> {
                            final AlgNode r = (AlgNode) args[0];
                            throw new JaninoRelMetadataProvider.NoHandler( r.getClass() );
                        } ) );
    }


    /**
     * Returns an instance of AlgMetadataQuery. It ensures that cycles do not occur while computing metadata.
     */
    public static AlgMetadataQuery instance() {
        return new AlgMetadataQuery( THREAD_PROVIDERS.get(), EMPTY );
    }


    /**
     * Creates and initializes the instance that will serve as a prototype for all other instances.
     */
    private AlgMetadataQuery( boolean dummy ) {
        this.metadataProvider = null;
        this.collationHandler = initialHandler( BuiltInMetadata.Collation.Handler.class );
        this.columnOriginHandler = initialHandler( BuiltInMetadata.ColumnOrigin.Handler.class );
        this.expressionLineageHandler = initialHandler( BuiltInMetadata.ExpressionLineage.Handler.class );
        this.tableReferencesHandler = initialHandler( BuiltInMetadata.TableReferences.Handler.class );
        this.columnUniquenessHandler = initialHandler( BuiltInMetadata.ColumnUniqueness.Handler.class );
        this.cumulativeCostHandler = initialHandler( BuiltInMetadata.CumulativeCost.Handler.class );
        this.distinctRowCountHandler = initialHandler( BuiltInMetadata.DistinctRowCount.Handler.class );
        this.distributionHandler = initialHandler( BuiltInMetadata.Distribution.Handler.class );
        this.explainVisibilityHandler = initialHandler( BuiltInMetadata.ExplainVisibility.Handler.class );
        this.maxRowCountHandler = initialHandler( BuiltInMetadata.MaxRowCount.Handler.class );
        this.minRowCountHandler = initialHandler( BuiltInMetadata.MinRowCount.Handler.class );
        this.memoryHandler = initialHandler( BuiltInMetadata.Memory.Handler.class );
        this.nonCumulativeCostHandler = initialHandler( BuiltInMetadata.NonCumulativeCost.Handler.class );
        this.parallelismHandler = initialHandler( BuiltInMetadata.Parallelism.Handler.class );
        this.percentageOriginalRowsHandler = initialHandler( BuiltInMetadata.PercentageOriginalRows.Handler.class );
        this.populationSizeHandler = initialHandler( BuiltInMetadata.PopulationSize.Handler.class );
        this.predicatesHandler = initialHandler( BuiltInMetadata.Predicates.Handler.class );
        this.allPredicatesHandler = initialHandler( BuiltInMetadata.AllPredicates.Handler.class );
        this.nodeTypesHandler = initialHandler( BuiltInMetadata.NodeTypes.Handler.class );
        this.rowCountHandler = initialHandler( TupleCount.Handler.class );
        this.selectivityHandler = initialHandler( BuiltInMetadata.Selectivity.Handler.class );
        this.sizeHandler = initialHandler( BuiltInMetadata.Size.Handler.class );
        this.uniqueKeysHandler = initialHandler( BuiltInMetadata.UniqueKeys.Handler.class );
    }


    /**
     * Re-generates the handler for a given kind of metadata, adding support for {@code class_} if it is not already present.
     */
    protected <M extends Metadata, H extends MetadataHandler<M>> H
    revise( Class<? extends AlgNode> class_, MetadataDef<M> def ) {
        return metadataProvider.revise( class_, def );
    }


    /**
     * Returns the {@link BuiltInMetadata.NodeTypes#getNodeTypes()} statistic.
     *
     * @param alg the algebra expression
     */
    public Multimap<Class<? extends AlgNode>, AlgNode> getNodeTypes( AlgNode alg ) {
        for ( ; ; ) {
            try {
                return nodeTypesHandler.getNodeTypes( alg, this );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                nodeTypesHandler = revise( e.algClass, BuiltInMetadata.NodeTypes.DEF );
            }
        }
    }


    /**
     * Returns the {@link TupleCount#getTupleCount()} statistic.
     *
     * @param alg the algebra expression
     * @return estimated tuple count, or null if no reliable estimate can be determined
     */
    public Optional<Double> getTupleCount( AlgNode alg ) {
        for ( ; ; ) {
            try {
                Double result = rowCountHandler.getTupleCount( alg, this );
                return Optional.ofNullable( validateResult( result ) );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                rowCountHandler = revise( e.algClass, TupleCount.DEF );
            } catch ( CyclicMetadataException e ) {
                log.warn( "Cyclic metadata detected while computing row count for {}", alg );
                return Optional.empty();
            }
        }
    }


    /**
     * Returns the {@link BuiltInMetadata.MaxRowCount#getMaxRowCount()} statistic.
     *
     * @param alg the algebra expression
     * @return max row count
     */
    public Double getMaxRowCount( AlgNode alg ) {
        for ( ; ; ) {
            try {
                return maxRowCountHandler.getMaxRowCount( alg, this );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                maxRowCountHandler = revise( e.algClass, BuiltInMetadata.MaxRowCount.DEF );
            }
        }
    }


    /**
     * Returns the {@link BuiltInMetadata.MinRowCount#getMinRowCount()} statistic.
     *
     * @param alg the algebra expression
     * @return max row count
     */
    public Double getMinRowCount( AlgNode alg ) {
        for ( ; ; ) {
            try {
                return minRowCountHandler.getMinRowCount( alg, this );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                minRowCountHandler = revise( e.algClass, BuiltInMetadata.MinRowCount.DEF );
            }
        }
    }


    /**
     * Returns the {@link BuiltInMetadata.CumulativeCost#getCumulativeCost()} statistic.
     *
     * @param alg the algebra expression
     * @return estimated cost, or null if no reliable estimate can be determined
     */
    public AlgOptCost getCumulativeCost( AlgNode alg ) {
        for ( ; ; ) {
            try {
                return cumulativeCostHandler.getCumulativeCost( alg, this );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                cumulativeCostHandler = revise( e.algClass, BuiltInMetadata.CumulativeCost.DEF );
            } catch ( CyclicMetadataException e ) {
                return alg.getCluster().getPlanner().getCostFactory().makeInfiniteCost();
            }
        }
    }


    /**
     * Returns the {@link BuiltInMetadata.NonCumulativeCost#getNonCumulativeCost()} statistic.
     *
     * @param alg the algebra expression
     * @return estimated cost, or null if no reliable estimate can be determined
     */
    public AlgOptCost getNonCumulativeCost( AlgNode alg ) {
        for ( ; ; ) {
            try {
                return nonCumulativeCostHandler.getNonCumulativeCost( alg, this );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                nonCumulativeCostHandler = revise( e.algClass, BuiltInMetadata.NonCumulativeCost.DEF );
            } catch ( CyclicMetadataException e ) {
                return alg.getCluster().getPlanner().getCostFactory().makeInfiniteCost();
            }
        }
    }


    /**
     * Returns the {@link BuiltInMetadata.PercentageOriginalRows#getPercentageOriginalRows()} statistic.
     *
     * @param alg the algebra expression
     * @return estimated percentage (between 0.0 and 1.0), or null if no reliable estimate can be determined
     */
    public Double getPercentageOriginalRows( AlgNode alg ) {
        for ( ; ; ) {
            try {
                Double result = percentageOriginalRowsHandler.getPercentageOriginalRows( alg, this );
                return validatePercentage( result );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                percentageOriginalRowsHandler = revise( e.algClass, BuiltInMetadata.PercentageOriginalRows.DEF );
            }
        }
    }


    /**
     * Returns the {@link BuiltInMetadata.ColumnOrigin#getColumnOrigins(int)} statistic.
     *
     * @param alg the algebra expression
     * @param column 0-based ordinal for output column of interest
     * @return set of origin columns, or null if this information cannot be determined (whereas empty set indicates definitely no origin columns at all)
     */
    public Set<AlgColumnOrigin> getColumnOrigins( AlgNode alg, int column ) {
        for ( ; ; ) {
            try {
                return columnOriginHandler.getColumnOrigins( alg, this, column );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                columnOriginHandler = revise( e.algClass, BuiltInMetadata.ColumnOrigin.DEF );
            }
        }
    }


    /**
     * Determines the origin of a column, provided the column maps to a single column that isn't derived.
     *
     * @param alg the {@link AlgNode} of the column
     * @param column the offset of the column whose origin we are trying to determine
     * @return the origin of a column provided it's a simple column; otherwise, returns null
     * @see #getColumnOrigins(AlgNode, int)
     */
    public AlgColumnOrigin getColumnOrigin( AlgNode alg, int column ) {
        final Set<AlgColumnOrigin> origins = getColumnOrigins( alg, column );
        if ( origins == null || origins.size() != 1 ) {
            return null;
        }
        final AlgColumnOrigin origin = Iterables.getOnlyElement( origins );
        return origin.isDerived() ? null : origin;
    }


    /**
     * Determines the origin of a column.
     */
    public Set<RexNode> getExpressionLineage( AlgNode alg, RexNode expression ) {
        for ( ; ; ) {
            try {
                return expressionLineageHandler.getExpressionLineage( alg, this, expression );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                expressionLineageHandler = revise( e.algClass, BuiltInMetadata.ExpressionLineage.DEF );
            }
        }
    }


    /**
     * Determines the tables used by a plan.
     */
    public Set<AlgTableRef> getTableReferences( AlgNode alg ) {
        for ( ; ; ) {
            try {
                return tableReferencesHandler.getTableReferences( alg, this );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                tableReferencesHandler = revise( e.algClass, BuiltInMetadata.TableReferences.DEF );
            }
        }
    }


    /**
     * Determines the origin of a {@link AlgNode}, provided it maps to a single table, optionally with filtering and projection.
     *
     * @param alg the AlgNode
     * @return the table, if the {@link AlgNode} is a simple table; otherwise null
     */
    public Entity getTableOrigin( AlgNode alg ) {
        // Determine the simple origin of the first column in the/ AlgNode. If it's simple, then that means that the underlying table is also simple, even if the column itself is derived.
        if ( alg.getTupleType().getFieldCount() == 0 ) {
            return null;
        }
        final Set<AlgColumnOrigin> colOrigins = getColumnOrigins( alg, 0 );
        if ( colOrigins == null || colOrigins.isEmpty() ) {
            return null;
        }
        return colOrigins.iterator().next().getOriginTable();
    }


    /**
     * Returns the {@link BuiltInMetadata.Selectivity#getSelectivity(RexNode)} statistic.
     *
     * @param alg the algebra expression
     * @param predicate predicate whose selectivity is to be estimated against {@code alg}'s output
     * @return estimated selectivity (between 0.0 and 1.0), or null if no reliable estimate can be determined
     */
    public Double getSelectivity( AlgNode alg, RexNode predicate ) {
        for ( ; ; ) {
            try {
                Double result = selectivityHandler.getSelectivity( alg, this, predicate );
                return validatePercentage( result );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                selectivityHandler = revise( e.algClass, BuiltInMetadata.Selectivity.DEF );
            }
        }
    }


    /**
     * Returns the {@link BuiltInMetadata.UniqueKeys#getUniqueKeys(boolean)} statistic.
     *
     * @param alg the algebra expression
     * @return set of keys, or null if this information cannot be determined (whereas empty set indicates definitely no keys at all)
     */
    public Set<ImmutableBitSet> getUniqueKeys( AlgNode alg ) {
        return getUniqueKeys( alg, false );
    }


    /**
     * Returns the {@link BuiltInMetadata.UniqueKeys#getUniqueKeys(boolean)} statistic.
     *
     * @param alg the algebra expression
     * @param ignoreNulls if true, ignore null values when determining whether the keys are unique
     * @return set of keys, or null if this information cannot be determined (whereas empty set indicates definitely no keys at all)
     */
    public Set<ImmutableBitSet> getUniqueKeys( AlgNode alg, boolean ignoreNulls ) {
        for ( ; ; ) {
            try {
                return uniqueKeysHandler.getUniqueKeys( alg, this, ignoreNulls );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                uniqueKeysHandler = revise( e.algClass, BuiltInMetadata.UniqueKeys.DEF );
            }
        }
    }


    /**
     * Returns whether the rows of a given algebra expression are distinct. This is derived by applying the {@link BuiltInMetadata.ColumnUniqueness#areColumnsUnique(ImmutableBitSet, boolean)}
     * statistic over all columns.
     *
     * @param alg the algebra expression
     * @return true or false depending on whether the rows are unique, or null if not enough information is available to make that determination
     */
    public Boolean areRowsUnique( AlgNode alg ) {
        final ImmutableBitSet columns = ImmutableBitSet.range( alg.getTupleType().getFieldCount() );
        return areColumnsUnique( alg, columns, false );
    }


    /**
     * Returns the {@link BuiltInMetadata.ColumnUniqueness#areColumnsUnique(ImmutableBitSet, boolean)} statistic.
     *
     * @param alg the algebra expression
     * @param columns column mask representing the subset of columns for which uniqueness will be determined
     * @return true or false depending on whether the columns are unique, or null if not enough information is available to make that determination
     */
    public Boolean areColumnsUnique( AlgNode alg, ImmutableBitSet columns ) {
        return areColumnsUnique( alg, columns, false );
    }


    /**
     * Returns the {@link BuiltInMetadata.ColumnUniqueness#areColumnsUnique(ImmutableBitSet, boolean)} statistic.
     *
     * @param alg the algebra expression
     * @param columns column mask representing the subset of columns for which uniqueness will be determined
     * @param ignoreNulls if true, ignore null values when determining column uniqueness
     * @return true or false depending on whether the columns are unique, or null if not enough information is available to make that determination
     */
    public Boolean areColumnsUnique( AlgNode alg, ImmutableBitSet columns, boolean ignoreNulls ) {
        for ( ; ; ) {
            try {
                return columnUniquenessHandler.areColumnsUnique( alg, this, columns, ignoreNulls );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                columnUniquenessHandler = revise( e.algClass, BuiltInMetadata.ColumnUniqueness.DEF );
            }
        }
    }


    /**
     * Returns the {@link BuiltInMetadata.Collation#collations()} statistic.
     *
     * @param alg the algebra expression
     * @return List of sorted column combinations, or null if not enough information is available to make that determination
     */
    public ImmutableList<AlgCollation> collations( AlgNode alg ) {
        for ( ; ; ) {
            try {
                return collationHandler.collations( alg, this );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                collationHandler = revise( e.algClass, BuiltInMetadata.Collation.DEF );
            }
        }
    }


    /**
     * Returns the {@link BuiltInMetadata.Distribution#distribution()} statistic.
     *
     * @param alg the algebra expression
     * @return List of sorted column combinations, or null if not enough information is available to make that determination
     */
    public AlgDistribution distribution( AlgNode alg ) {
        for ( ; ; ) {
            try {
                return distributionHandler.distribution( alg, this );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                distributionHandler = revise( e.algClass, BuiltInMetadata.Distribution.DEF );
            }
        }
    }


    /**
     * Returns the {@link BuiltInMetadata.PopulationSize#getPopulationSize(ImmutableBitSet)} statistic.
     *
     * @param alg the algebra expression
     * @param groupKey column mask representing the subset of columns for which the row count will be determined
     * @return distinct row count for the given groupKey, or null if no reliable estimate can be determined
     */
    public Double getPopulationSize(
            AlgNode alg,
            ImmutableBitSet groupKey ) {
        for ( ; ; ) {
            try {
                Double result = populationSizeHandler.getPopulationSize( alg, this, groupKey );
                return validateResult( result );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                populationSizeHandler = revise( e.algClass, BuiltInMetadata.PopulationSize.DEF );
            }
        }
    }


    /**
     * Returns the {@link BuiltInMetadata.Size#averageRowSize()} statistic.
     *
     * @param alg the algebra expression
     * @return average size of a row, in bytes, or null if not known
     */
    public Double getAverageRowSize( AlgNode alg ) {
        for ( ; ; ) {
            try {
                return sizeHandler.averageRowSize( alg, this );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                sizeHandler = revise( e.algClass, BuiltInMetadata.Size.DEF );
            }
        }
    }


    /**
     * Returns the {@link BuiltInMetadata.Size#averageColumnSizes()} statistic.
     *
     * @param alg the algebra expression
     * @return a list containing, for each column, the average size of a column value, in bytes. Each value or the entire list may be null if the metadata is not available
     */
    public List<Double> getAverageColumnSizes( AlgNode alg ) {
        for ( ; ; ) {
            try {
                return sizeHandler.averageColumnSizes( alg, this );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                sizeHandler = revise( e.algClass, BuiltInMetadata.Size.DEF );
            }
        }
    }


    /**
     * As {@link #getAverageColumnSizes(AlgNode)} but never returns a null list, only ever a list of nulls.
     */
    public List<Double> getAverageColumnSizesNotNull( AlgNode alg ) {
        final List<Double> averageColumnSizes = getAverageColumnSizes( alg );
        return averageColumnSizes == null
                ? Collections.nCopies( alg.getTupleType().getFieldCount(), null )
                : averageColumnSizes;
    }


    /**
     * Returns the {@link BuiltInMetadata.Parallelism#isPhaseTransition()} statistic.
     *
     * @param alg the algebra expression
     * @return whether each physical operator implementing this algebra expression belongs to a different process than its inputs, or null if not known
     */
    public Boolean isPhaseTransition( AlgNode alg ) {
        for ( ; ; ) {
            try {
                return parallelismHandler.isPhaseTransition( alg, this );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                parallelismHandler = revise( e.algClass, BuiltInMetadata.Parallelism.DEF );
            }
        }
    }


    /**
     * Returns the {@link BuiltInMetadata.Parallelism#splitCount()} statistic.
     *
     * @param alg the algebra expression
     * @return the number of distinct splits of the data, or null if not known
     */
    public Integer splitCount( AlgNode alg ) {
        for ( ; ; ) {
            try {
                return parallelismHandler.splitCount( alg, this );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                parallelismHandler = revise( e.algClass, BuiltInMetadata.Parallelism.DEF );
            }
        }
    }


    /**
     * Returns the {@link BuiltInMetadata.Memory#memory()} statistic.
     *
     * @param alg the algebra expression
     * @return the expected amount of memory, in bytes, required by a physical operator implementing this algebra expression, across all splits, or null if not known
     */
    public Double memory( AlgNode alg ) {
        for ( ; ; ) {
            try {
                return memoryHandler.memory( alg, this );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                memoryHandler = revise( e.algClass, BuiltInMetadata.Memory.DEF );
            }
        }
    }


    /**
     * Returns the {@link BuiltInMetadata.Memory#cumulativeMemoryWithinPhase()} statistic.
     *
     * @param alg the algebra expression
     * @return the cumulative amount of memory, in bytes, required by the physical operator implementing this algebra expression, and all other operators within the same phase, across all splits, or null if not known
     */
    public Double cumulativeMemoryWithinPhase( AlgNode alg ) {
        for ( ; ; ) {
            try {
                return memoryHandler.cumulativeMemoryWithinPhase( alg, this );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                memoryHandler = revise( e.algClass, BuiltInMetadata.Memory.DEF );
            }
        }
    }


    /**
     * Returns the {@link BuiltInMetadata.Memory#cumulativeMemoryWithinPhaseSplit()} statistic.
     *
     * @param alg the algebra expression
     * @return the expected cumulative amount of memory, in bytes, required by the physical operator implementing this algebra expression, and all operators within the same phase, within each split, or null if not known
     */
    public Double cumulativeMemoryWithinPhaseSplit( AlgNode alg ) {
        for ( ; ; ) {
            try {
                return memoryHandler.cumulativeMemoryWithinPhaseSplit( alg, this );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                memoryHandler = revise( e.algClass, BuiltInMetadata.Memory.DEF );
            }
        }
    }


    /**
     * Returns the {@link BuiltInMetadata.DistinctRowCount#getDistinctRowCount(ImmutableBitSet, RexNode)} statistic.
     *
     * @param alg the algebra expression
     * @param groupKey column mask representing group by columns
     * @param predicate pre-filtered predicates
     * @return distinct row count for groupKey, filtered by predicate, or null if no reliable estimate can be determined
     */
    public Double getDistinctRowCount( AlgNode alg, ImmutableBitSet groupKey, RexNode predicate ) {
        for ( ; ; ) {
            try {
                Double result = distinctRowCountHandler.getDistinctRowCount( alg, this, groupKey, predicate );
                return validateResult( result );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                distinctRowCountHandler = revise( e.algClass, BuiltInMetadata.DistinctRowCount.DEF );
            }
        }
    }


    /**
     * Returns the {@link BuiltInMetadata.Predicates#getPredicates()} statistic.
     *
     * @param alg the algebra expression
     * @return Predicates that can be pulled above this AlgNode
     */
    public AlgOptPredicateList getPulledUpPredicates( AlgNode alg ) {
        for ( ; ; ) {
            try {
                return predicatesHandler.getPredicates( alg, this );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                predicatesHandler = revise( e.algClass, BuiltInMetadata.Predicates.DEF );
            }
        }
    }


    /**
     * Returns the {@link BuiltInMetadata.AllPredicates#getAllPredicates()} statistic.
     *
     * @param alg the algebra expression
     * @return All predicates within and below this AlgNode
     */
    public AlgOptPredicateList getAllPredicates( AlgNode alg ) {
        for ( ; ; ) {
            try {
                return allPredicatesHandler.getAllPredicates( alg, this );
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                allPredicatesHandler = revise( e.algClass, BuiltInMetadata.AllPredicates.DEF );
            }
        }
    }


    /**
     * Returns the {@link BuiltInMetadata.ExplainVisibility#isVisibleInExplain(ExplainLevel)} statistic.
     *
     * @param alg the algebra expression
     * @param explainLevel level of detail
     * @return true for visible, false for invisible; if no metadata is available, defaults to true
     */
    public boolean isVisibleInExplain( AlgNode alg, ExplainLevel explainLevel ) {
        for ( ; ; ) {
            try {
                Boolean b = explainVisibilityHandler.isVisibleInExplain( alg, this, explainLevel );
                return b == null || b;
            } catch ( JaninoRelMetadataProvider.NoHandler e ) {
                explainVisibilityHandler = revise( e.algClass, BuiltInMetadata.ExplainVisibility.DEF );
            }
        }
    }


    private static Double validatePercentage( Double result ) {
        assert isPercentage( result, true );
        return result;
    }


    /**
     * Returns the {@link BuiltInMetadata.Distribution#distribution()} statistic.
     *
     * @param alg the algebra expression
     * @return description of how the rows in the algebra expression are physically distributed
     */
    public AlgDistribution getDistribution( AlgNode alg ) {
        final BuiltInMetadata.Distribution metadata = alg.metadata( BuiltInMetadata.Distribution.class, this );
        return metadata.distribution();
    }


    private static boolean isPercentage( Double result, boolean fail ) {
        if ( result != null ) {
            final double d = result;
            if ( d < 0.0 ) {
                assert !fail;
                return false;
            }
            if ( d > 1.0 ) {
                assert !fail;
                return false;
            }
        }
        return true;
    }


    private static boolean isNonNegative( Double result, boolean fail ) {
        if ( result != null ) {
            final double d = result;
            if ( d < 0.0 ) {
                assert !fail;
                return false;
            }
        }
        return true;
    }


    @Nullable
    private static Double validateResult( Double result ) {
        if ( result == null ) {
            return null;
        }

        // Never let the result go below 1, as it will result in incorrect calculations if the row-count is used as the denominator in a division expression.
        // Also, cap the value at the max double value to avoid calculations using infinity.
        if ( result.isInfinite() ) {
            result = Double.MAX_VALUE;
        }
        assert isNonNegative( result, true );
        if ( result < 1.0 ) {
            result = 1.0;
        }
        return result;
    }

}

