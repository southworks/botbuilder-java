// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.bot.builder;

/**
 * Tuple class containing an HTTP Status Code and a JSON Serializable object.
 * The HTTP Status code is, in the invoke activity scenario, what will be set in
 * the resulting POST. The Body of the resulting POST will be the JSON
 * Serialized content from the Body property.
 */
@Deprecated // Use the one of bot-schema
public class InvokeResponse extends com.microsoft.bot.schema.InvokeResponse {
    public InvokeResponse(int withStatus, Object withBody) {
        super(withStatus, withBody);
    }
}
