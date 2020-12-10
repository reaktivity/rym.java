/**
 * Copyright 2016-2020 The Reaktivity Project
 *
 * The Reaktivity Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.reaktivity.rym.internal;

import java.nio.file.Paths;

import com.github.rvesse.airline.model.ArgumentsMetadata;
import com.github.rvesse.airline.model.OptionMetadata;
import com.github.rvesse.airline.parser.ParseState;
import com.github.rvesse.airline.types.TypeConverter;
import com.github.rvesse.airline.types.TypeConverterProvider;
import com.github.rvesse.airline.types.numerics.NumericTypeConverter;

public final class RymPathConverterProvider implements TypeConverterProvider
{
    private final RymPathConverter converter = new RymPathConverter();

    private final class RymPathConverter implements TypeConverter
    {
        @Override
        public void setNumericConverter(
            NumericTypeConverter converter)
        {
        }

        @Override
        public Object convert(
            String name,
            Class<?> type,
            String value)
        {
            return Paths.get(value);
        }
    }

    @Override
    public <T> TypeConverter getTypeConverter(
        OptionMetadata option,
        ParseState<T> state)
    {
        return converter;
    }

    @Override
    public <T> TypeConverter getTypeConverter(
        ArgumentsMetadata arguments,
        ParseState<T> state)
    {
        return converter;
    }
}
