/**
 * Copyright (C) 2009-2010 the original author or authors.
 * See the notice.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fusesource.restygwt.client.callback;

import java.util.List;
import java.util.logging.Logger;

import org.fusesource.restygwt.client.Method;
import org.fusesource.restygwt.client.cache.QueueableCacheStorage;
import org.fusesource.restygwt.client.dispatcher.CacheKey;

import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.Response;
import com.google.gwt.logging.client.LogConfiguration;

public class CachingCallbackFilter implements CallbackFilter {

    protected QueueableCacheStorage cache;

    public CachingCallbackFilter(QueueableCacheStorage cache) {
        this.cache = cache;
    }

    /**
     * the real filter method, called independent of the response code
     *
     * TODO method.getResponse() is not equal to response. unfortunately
     */
    @Override
    public RequestCallback filter(final Method method, final Response response,
            RequestCallback callback) {
        final int code = response.getStatusCode();

        final CacheKey ck = new CacheKey(method.builder);
        final List<RequestCallback> removedCallbacks = cache.removeCallbacks(ck);

        if (removedCallbacks != null
                && 1 < removedCallbacks.size()) {
            // remove the first callback from list, as this is called explicitly
            removedCallbacks.remove(0);
            // fetch the builders callback and wrap it with a new one, calling all others too
            final RequestCallback originalCallback = callback;

            callback = new RequestCallback() {
                @Override
                public void onResponseReceived(Request request, Response response) {
                    // call the original callback
                    if (LogConfiguration.loggingIsEnabled()) {
                        Logger.getLogger(CachingCallbackFilter.class.getName())
                                .finer("call original callback for " + ck);
                    }
                    originalCallback.onResponseReceived(request, response);

                    if (LogConfiguration.loggingIsEnabled()) {
                        Logger.getLogger(CachingCallbackFilter.class.getName())
                                .finer("call "+ removedCallbacks.size()
                                        + " more queued callbacks for " + ck);
                    }

                    // and all the others, found in cache
                    for (RequestCallback cb : removedCallbacks) {
                        cb.onResponseReceived(request, response);
                    }
                }

                @Override
                public void onError(Request request, Throwable exception) {
                    if (LogConfiguration.loggingIsEnabled()) {
                        Logger.getLogger(CachingCallbackFilter.class.getName())
                                .severe("cannot call " + (removedCallbacks.size()+1)
                                        + " callbacks for " + ck + " due to error: "
                                        + exception.getMessage());
                    }
                    // call the original callback
                    if (LogConfiguration.loggingIsEnabled()) {
                        Logger.getLogger(CachingCallbackFilter.class.getName())
                                .finer("call original callback for " + ck);
                    }

                    originalCallback.onError(request, exception);

                    if (LogConfiguration.loggingIsEnabled()) {
                        Logger.getLogger(CachingCallbackFilter.class.getName())
                                .finer("call "+ removedCallbacks.size()
                                        + " more queued callbacks for " + ck);
                    }

                    // and all the others, found in cache
                    for (RequestCallback cb : removedCallbacks) {
                        cb.onError(request, exception);
                    }
                }
            };
        } else {
            if (LogConfiguration.loggingIsEnabled()) {
                Logger.getLogger(CachingCallbackFilter.class.getName()).finer("removed one or no " +
                        "callback for cachekey " + ck);
            }
        }

        if (code < Response.SC_MULTIPLE_CHOICES
                && code >= Response.SC_OK) {
            if (LogConfiguration.loggingIsEnabled()) {
                Logger.getLogger(CachingCallbackFilter.class.getName()).finer("cache to " + ck
                        + ": " + response);
            }
            cache.putResult(ck, response);
            return callback;
        }

        if (LogConfiguration.loggingIsEnabled()) {
            Logger.getLogger(CachingCallbackFilter.class.getName())
                    .info("cannot cache due to invalid response code: " + code);
        }
        return callback;
    }
}
