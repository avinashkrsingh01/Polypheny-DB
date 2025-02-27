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
 */

package org.polypheny.db.algebra.logical.lpg;

import java.util.List;
import javax.annotation.Nullable;
import lombok.Getter;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.AlgShuttle;
import org.polypheny.db.algebra.core.lpg.LpgUnwind;
import org.polypheny.db.algebra.polyalg.arguments.IntArg;
import org.polypheny.db.algebra.polyalg.arguments.PolyAlgArgs;
import org.polypheny.db.algebra.polyalg.arguments.StringArg;
import org.polypheny.db.plan.AlgCluster;
import org.polypheny.db.plan.AlgTraitSet;


@Getter
public class LogicalLpgUnwind extends LpgUnwind {


    /**
     * Subclass of {@link LpgUnwind} not targeted at any particular engine or calling convention.
     */
    public LogicalLpgUnwind( AlgCluster cluster, AlgTraitSet traits, AlgNode input, int index, @Nullable String alias ) {
        super( cluster, traits, input, index, alias );
    }


    public static LogicalLpgUnwind create( AlgNode input, int index, String alias ) {
        return new LogicalLpgUnwind( input.getCluster(), input.getTraitSet(), input, index, alias );
    }


    public static LogicalLpgUnwind create( PolyAlgArgs args, List<AlgNode> children, AlgCluster cluster ) {
        return create( children.get( 0 ),
                args.getArg( "index", IntArg.class ).getArg(),
                args.getArg( "alias", StringArg.class ).getArg() );
    }


    @Override
    public AlgNode copy( AlgTraitSet traitSet, List<AlgNode> inputs ) {
        return new LogicalLpgUnwind( inputs.get( 0 ).getCluster(), traitSet, inputs.get( 0 ), index, alias );
    }


    @Override
    public AlgNode accept( AlgShuttle shuttle ) {
        return shuttle.visit( this );
    }


    @Override
    public PolyAlgArgs bindArguments() {
        PolyAlgArgs args = new PolyAlgArgs( getPolyAlgDeclaration() );
        return args.put( "index", new IntArg( index ) )
                .put( "alias", new StringArg( alias ) );
    }

}
