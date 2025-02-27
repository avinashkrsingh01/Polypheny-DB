/*
 * Copyright 2019-2021 The Polypheny Project
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

package org.polypheny.db.rex;


import java.util.List;
import java.util.Objects;
import org.polypheny.db.algebra.constant.Kind;
import org.polypheny.db.algebra.type.AlgDataType;


/**
 * Local variable.
 *
 * Identity is based upon type and index. We want multiple references to the same slot in the same context to be equal. A side effect is that references to slots in different contexts which happen to
 * have the same index and type will be considered equal; this is not desired, but not too damaging, because of the immutability.
 *
 * Variables are immutable.
 */
public class RexLocalRef extends RexSlot {

    public static final String PREFIX = "$t";

    // array of common names, to reduce memory allocations
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private static final List<String> NAMES = new SelfPopulatingList( PREFIX, 30 );


    /**
     * Creates a local variable.
     *
     * @param index Index of the field in the underlying row type
     * @param type Type of the column
     */
    public RexLocalRef( int index, AlgDataType type ) {
        super( createName( index ), index, type );
        assert type != null;
        assert index >= 0;
    }


    @Override
    public Kind getKind() {
        return Kind.LOCAL_REF;
    }


    public boolean equals( Object obj ) {
        return this == obj
                || obj instanceof RexLocalRef
                && this.type == ((RexLocalRef) obj).type
                && this.index == ((RexLocalRef) obj).index;
    }


    public int hashCode() {
        return Objects.hash( type, index );
    }


    @Override
    public <R> R accept( RexVisitor<R> visitor ) {
        return visitor.visitLocalRef( this );
    }


    @Override
    public <R, P> R accept( RexBiVisitor<R, P> visitor, P arg ) {
        return visitor.visitLocalRef( this, arg );
    }


    private static String createName( int index ) {
        return NAMES.get( index );
    }

}
