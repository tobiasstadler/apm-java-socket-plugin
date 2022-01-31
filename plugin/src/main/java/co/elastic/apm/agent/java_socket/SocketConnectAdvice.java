/*
   Copyright 2021 Tobias Stadler

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package co.elastic.apm.agent.java_socket;

import co.elastic.apm.api.AbstractSpanImplAccessor;
import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Outcome;
import co.elastic.apm.api.Scope;
import co.elastic.apm.api.Span;
import net.bytebuddy.asm.Advice;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class SocketConnectAdvice {

    private static final MethodHandle isExit = getMethodHandle("co.elastic.apm.agent.impl.transaction.AbstractSpan", "isExit", boolean.class);

    private static MethodHandle getMethodHandle(String clazz, String method, Class<?> rtype) {
        try {
            return MethodHandles.publicLookup()
                    .findVirtual(Class.forName(clazz), method, MethodType.methodType(rtype));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException ignored) {
            return null;
        }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnterConnect(@Advice.Argument(0) SocketAddress endpoint) throws Throwable {
        if (isExit == null || !(endpoint instanceof InetSocketAddress)) {
            return null;
        }

        InetSocketAddress inetSocketAddress = (InetSocketAddress) endpoint;

        Span span = ElasticApm.currentSpan();
        if (span.getId().isEmpty()) {
            return null;
        }

        Object spanImpl = AbstractSpanImplAccessor.getAgentImpl(span);
        if ((boolean) isExit.invoke(spanImpl)) {
            return null;
        }

        return span.startExitSpan("network", "connect", null)
                .setName("connect to " + inetSocketAddress.getAddress().getHostAddress() + ":" + inetSocketAddress.getPort())
                .setDestinationAddress(inetSocketAddress.getAddress().getHostAddress(), inetSocketAddress.getPort())
                .activate();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExitConnect(@Advice.Enter Object scope, @Advice.Thrown Throwable t) {
        try {
            Span span = ElasticApm.currentSpan();
            if (t != null) {
                span.captureException(t);
                span.setOutcome(Outcome.FAILURE);
            } else {
                span.setOutcome(Outcome.SUCCESS);
            }
            span.end();
        } finally {
            ((Scope) scope).close();
        }
    }
}
