/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.r2dbc.postgresql.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.r2dbc.postgresql.client.Parameter;
import io.r2dbc.postgresql.message.Format;
import io.r2dbc.postgresql.type.PostgresqlObjectId;
import io.r2dbc.postgresql.util.Assert;
import io.r2dbc.postgresql.util.ByteBufUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

abstract class AbstractGeometryCodec<T> extends AbstractCodec<T> {

    protected final PostgresqlObjectId postgresqlObjectId;

    protected final ByteBufAllocator byteBufAllocator;

    AbstractGeometryCodec(Class<T> type, PostgresqlObjectId postgresqlObjectId, ByteBufAllocator byteBufAllocator) {
        super(type);
        this.postgresqlObjectId = Assert.requireNonNull(postgresqlObjectId, "postgresqlObjectId must not be null");
        this.byteBufAllocator = Assert.requireNonNull(byteBufAllocator, "byteBufAllocator must not be null");
    }

    abstract T doDecodeBinary(ByteBuf byteBuffer);

    abstract T doDecodeText(String text);

    abstract ByteBuf doEncodeBinary(T value);

    @Override
    boolean doCanDecode(PostgresqlObjectId type, Format format) {
        Assert.requireNonNull(type, "type must not be null");
        Assert.requireNonNull(format, "format must not be null");

        return this.postgresqlObjectId == type;
    }

    @Override
    T doDecode(ByteBuf buffer, PostgresqlObjectId dataType, Format format, Class<? extends T> type) {
        Assert.requireNonNull(buffer, "byteBuf must not be null");
        Assert.requireNonNull(type, "type must not be null");
        Assert.requireNonNull(format, "format must not be null");

        if (format == Format.FORMAT_BINARY) {
            return doDecodeBinary(buffer);
        }

        return doDecodeText(ByteBufUtils.decode(buffer));
    }

    @Override
    Parameter doEncode(T value) {
        Assert.requireNonNull(value, "value must not be null");

        return create(this.postgresqlObjectId, Format.FORMAT_BINARY, () -> doEncodeBinary(value));
    }

    @Override
    public Parameter encodeNull() {
        return createNull(this.postgresqlObjectId, Format.FORMAT_BINARY);
    }

    /**
     * Create a {@link TokenStream} given {@code content}.
     *
     * @param content the textual representation
     * @return a {@link TokenStream} providing access to a tokenized form of the data structure.
     */
    TokenStream getTokenStream(String content) {

        List<String> tokens = tokenizeTextData(content);

        return new TokenStream() {

            int position = 0;

            @Override
            public boolean hasNext() {
                return tokens.size() > this.position;
            }

            @Override
            public String next() {

                if (hasNext()) {
                    return tokens.get(this.position++);
                }

                throw new IllegalStateException(String.format("No token available at index %d. Current tokens are: %s", this.position, tokens));
            }

            @Override
            public double nextDouble() {
                return Double.parseDouble(next());
            }

        };
    }

    /**
     * Remove token wrappers such as {@code <>}, {@code []}, {@code {}}, {@code ()} and extract tokens into a {@link List}.
     *
     * @param content the content to decode.
     * @return tokenized content.
     */
    private static List<String> tokenizeTextData(String content) {

        List<String> tokens = new ArrayList<>();

        for (int i = 0, s = 0; i < content.length(); i++) {

            char c = content.charAt(i);

            if (c == '(' || c == '[' || c == '<' || c == '{') {
                s++;
                continue;
            }

            if (c == ',' || c == ')' || c == ']' || c == '>' || c == '}') {
                if (s != i) {
                    tokens.add(content.substring(s, i));
                    s = i + 1;
                } else {
                    s++;
                }
            }
        }

        return tokens;
    }

    /**
     * Stream of tokens that represent individual components of the underlying data structure.
     */
    interface TokenStream extends Iterator<String> {

        /**
         * Advance the stream and return the next token.
         *
         * @return the token.
         * @throws IllegalStateException if the token stream is exhausted.
         * @see #hasNext()
         */
        @Override
        String next();

        /**
         * Advance the stream and return the next token as {@code double} value.
         *
         * @return the token.
         * @throws IllegalStateException if the token stream is exhausted.
         * @see #hasNext()
         */
        double nextDouble();

    }

}
