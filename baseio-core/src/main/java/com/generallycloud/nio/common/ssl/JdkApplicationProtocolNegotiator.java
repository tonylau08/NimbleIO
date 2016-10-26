package com.generallycloud.nio.common.ssl;

import javax.net.ssl.SSLEngine;
import java.util.List;
import java.util.Set;

public interface JdkApplicationProtocolNegotiator extends ApplicationProtocolNegotiator {
    /**
     * Abstract factory pattern for wrapping an {@link SSLEngine} object. This is useful for NPN/APLN JDK support.
     */
    interface SslEngineWrapperFactory {
        /**
         * Abstract factory pattern for wrapping an {@link SSLEngine} object. This is useful for NPN/APLN support.
         *
         * @param engine The engine to wrap.
         * @param applicationNegotiator The application level protocol negotiator
         * @param isServer <ul>
         * <li>{@code true} if the engine is for server side of connections</li>
         * <li>{@code false} if the engine is for client side of connections</li>
         * </ul>
         * @return The resulting wrapped engine. This may just be {@code engine}.
         */
        SSLEngine wrapSslEngine(SSLEngine engine, JdkApplicationProtocolNegotiator applicationNegotiator,
                boolean isServer);
    }

    /**
     * Interface to define the role of an application protocol selector in the SSL handshake process. Either
     * {@link ProtocolSelector#unsupported()} OR {@link ProtocolSelector#select(List)} will be called for each SSL
     * handshake.
     */
    interface ProtocolSelector {
        /**
         * Callback invoked to let the application know that the peer does not support this
         * {@link ApplicationProtocolNegotiator}.
         */
        void unsupported();

        /**
         * Callback invoked to select the application level protocol from the {@code protocols} provided.
         *
         * @param protocols the protocols sent by the protocol advertiser
         * @return the protocol selected by this {@link ProtocolSelector}. A {@code null} value will indicate the no
         * protocols were selected but the handshake should not fail. The decision to fail the handshake is left to the
         * other end negotiating the SSL handshake.
         * @throws Exception If the {@code protocols} provide warrant failing the SSL handshake with a fatal alert.
         */
        String select(List<String> protocols) throws Exception;
    }

    /**
     * A listener to be notified by which protocol was select by its peer. Either the
     * {@link ProtocolSelectionListener#unsupported()} OR the {@link ProtocolSelectionListener#selected(String)} method
     * will be called for each SSL handshake.
     */
    interface ProtocolSelectionListener {
        /**
         * Callback invoked to let the application know that the peer does not support this
         * {@link ApplicationProtocolNegotiator}.
         */
        void unsupported();

        /**
         * Callback invoked to let this application know the protocol chosen by the peer.
         *
         * @param protocol the protocol selected by the peer. May be {@code null} or empty as supported by the
         * application negotiation protocol.
         * @throws Exception This may be thrown if the selected protocol is not acceptable and the desired behavior is
         * to fail the handshake with a fatal alert.
         */
        void selected(String protocol) throws Exception;
    }

    /**
     * Factory interface for {@link ProtocolSelector} objects.
     */
    interface ProtocolSelectorFactory {
        /**
         * Generate a new instance of {@link ProtocolSelector}.
         * @param engine The {@link SSLEngine} that the returned {@link ProtocolSelector} will be used to create an
         * instance for.
         * @param supportedProtocols The protocols that are supported.
         * @return A new instance of {@link ProtocolSelector}.
         */
        ProtocolSelector newSelector(SSLEngine engine, Set<String> supportedProtocols);
    }

    /**
     * Factory interface for {@link ProtocolSelectionListener} objects.
     */
    interface ProtocolSelectionListenerFactory {
        /**
         * Generate a new instance of {@link ProtocolSelectionListener}.
         * @param engine The {@link SSLEngine} that the returned {@link ProtocolSelectionListener} will be used to
         * create an instance for.
         * @param supportedProtocols The protocols that are supported in preference order.
         * @return A new instance of {@link ProtocolSelectionListener}.
         */
        ProtocolSelectionListener newListener(SSLEngine engine, List<String> supportedProtocols);
    }

    /**
     * Get the {@link SslEngineWrapperFactory}.
     */
    SslEngineWrapperFactory wrapperFactory();

    /**
     * Get the {@link ProtocolSelectorFactory}.
     */
    ProtocolSelectorFactory protocolSelectorFactory();

    /**
     * Get the {@link ProtocolSelectionListenerFactory}.
     */
    ProtocolSelectionListenerFactory protocolListenerFactory();
}
