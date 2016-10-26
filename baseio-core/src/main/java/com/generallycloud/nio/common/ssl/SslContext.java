package com.generallycloud.nio.common.ssl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManagerFactory;

import com.generallycloud.nio.common.ssl.ApplicationProtocolConfig.Protocol;
import com.generallycloud.nio.common.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import com.generallycloud.nio.common.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import com.generallycloud.nio.component.BaseContext;
import com.generallycloud.nio.component.ByteArrayInputStream;

/**
 * A secure socket protocol implementation which acts as a factory for
 * {@link SSLEngine} and {@link SslHandler}. Internally, it is implemented via
 * JDK's {@link SSLContext} or OpenSSL's {@code SSL_CTX}.
 *
 * <h3>Making your server support SSL/TLS</h3>
 * 
 * <pre>
 * // In your {@link ChannelInitializer}:
 * {@link ChannelPipeline} p = channel.pipeline();
 * {@link SslContext} sslCtx = {@link SslContextBuilder#forServer(File, File) SslContextBuilder.forServer(...)}.build();
 * p.addLast("ssl", {@link #newHandler(ByteBufAllocator) sslCtx.newHandler(channel.alloc())});
 * ...
 * </pre>
 *
 * <h3>Making your client support SSL/TLS</h3>
 * 
 * <pre>
 * // In your {@link ChannelInitializer}:
 * {@link ChannelPipeline} p = channel.pipeline();
 * {@link SslContext} sslCtx = {@link SslContextBuilder#forClient() SslContextBuilder.forClient()}.build();
 * p.addLast("ssl", {@link #newHandler(ByteBufAllocator, String, int) sslCtx.newHandler(channel.alloc(), host, port)});
 * ...
 * </pre>
 */
public abstract class SslContext {
	static final CertificateFactory	X509_CERT_FACTORY;
	static {
		try {
			X509_CERT_FACTORY = CertificateFactory.getInstance("X.509");
		} catch (CertificateException e) {
			throw new IllegalStateException("unable to instance X.509 CertificateFactory", e);
		}
	}

	private BaseContext	 context;
	
	private SslHandler sslHandler;

	/**
	 * Returns the default server-side implementation provider currently in
	 * use.
	 *
	 * @return {@link SslProvider#OPENSSL} if OpenSSL is available.
	 *         {@link SslProvider#JDK} otherwise.
	 */
	public static SslProvider defaultServerProvider() {
		return defaultProvider();
	}

	/**
	 * Returns the default client-side implementation provider currently in
	 * use.
	 *
	 * @return {@link SslProvider#OPENSSL} if OpenSSL is available.
	 *         {@link SslProvider#JDK} otherwise.
	 */
	public static SslProvider defaultClientProvider() {
		return defaultProvider();
	}

	private static SslProvider defaultProvider() {
		if (OpenSsl.isAvailable()) {
			return SslProvider.OPENSSL;
		} else {
			return SslProvider.JDK;
		}
	}

	public void initialize(BaseContext context){
		this.context = context;
		this.sslHandler = new SslHandler(context);
	}
	
	static SslContext newServerContextInternal(SslProvider provider, X509Certificate[] trustCertCollection,
			TrustManagerFactory trustManagerFactory, X509Certificate[] keyCertChain, PrivateKey key,
			String keyPassword, KeyManagerFactory keyManagerFactory, Iterable<String> ciphers,
			CipherSuiteFilter cipherFilter, ApplicationProtocolConfig apn, long sessionCacheSize,
			long sessionTimeout, ClientAuth clientAuth, boolean startTls) throws SSLException {

		if (provider == null) {
			provider = defaultServerProvider();
		}

		switch (provider) {
		case JDK:
			return new JdkSslServerContext(trustCertCollection, trustManagerFactory, keyCertChain, key, keyPassword,
					keyManagerFactory, ciphers, cipherFilter, apn, sessionCacheSize, sessionTimeout, clientAuth,
					startTls);
		case OPENSSL:
			return null;
		case OPENSSL_REFCNT:
			return null;
		default:
			throw new Error(provider.toString());
		}
	}

	static SslContext newClientContextInternal(SslProvider provider, X509Certificate[] trustCert,
			TrustManagerFactory trustManagerFactory, X509Certificate[] keyCertChain, PrivateKey key,
			String keyPassword, KeyManagerFactory keyManagerFactory, Iterable<String> ciphers,
			CipherSuiteFilter cipherFilter, ApplicationProtocolConfig apn, long sessionCacheSize, long sessionTimeout)
			throws SSLException {
		if (provider == null) {
			provider = defaultClientProvider();
		}
		switch (provider) {
		case JDK:
			return new JdkSslClientContext(trustCert, trustManagerFactory, keyCertChain, key, keyPassword,
					keyManagerFactory, ciphers, cipherFilter, apn, sessionCacheSize, sessionTimeout);
		case OPENSSL:
			return null;
		case OPENSSL_REFCNT:
			return null;
		default:
			throw new Error(provider.toString());
		}
	}

	static ApplicationProtocolConfig toApplicationProtocolConfig(Iterable<String> nextProtocols) {
		ApplicationProtocolConfig apn;
		if (nextProtocols == null) {
			apn = ApplicationProtocolConfig.DISABLED;
		} else {
			apn = new ApplicationProtocolConfig(Protocol.NPN_AND_ALPN,
					SelectorFailureBehavior.CHOOSE_MY_LAST_PROTOCOL, SelectedListenerFailureBehavior.ACCEPT,
					nextProtocols);
		}
		return apn;
	}

	/**
	 * Returns {@code true} if and only if this context is for server-side.
	 */
	public final boolean isServer() {
		return !isClient();
	}

	/**
	 * Returns the {@code true} if and only if this context is for client-side.
	 */
	public abstract boolean isClient();

	/**
	 * Returns the list of enabled cipher suites, in the order of preference.
	 */
	public abstract List<String> cipherSuites();

	/**
	 * Returns the size of the cache used for storing SSL session objects.
	 */
	public abstract long sessionCacheSize();

	/**
	 * Returns the timeout for the cached SSL session objects, in seconds.
	 */
	public abstract long sessionTimeout();

	/**
	 * @deprecated Use {@link #applicationProtocolNegotiator()} instead.
	 */
	@Deprecated
	public final List<String> nextProtocols() {
		return applicationProtocolNegotiator().protocols();
	}

	/**
	 * Returns the object responsible for negotiating application layer
	 * protocols for the TLS NPN/ALPN extensions.
	 */
	public abstract ApplicationProtocolNegotiator applicationProtocolNegotiator();

	public abstract SSLEngine newEngine();

	public abstract SSLEngine newEngine(String peerHost, int peerPort);

	/**
	 * Returns the {@link SSLSessionContext} object held by this context.
	 */
	public abstract SSLSessionContext sessionContext();

	/**
	 * Generates a key specification for an (encrypted) private key.
	 *
	 * @param password
	 *             characters, if {@code null} an unencrypted key is assumed
	 * @param key
	 *             bytes of the DER encoded private key
	 *
	 * @return a key specification
	 *
	 * @throws IOException
	 *              if parsing {@code key} fails
	 * @throws NoSuchAlgorithmException
	 *              if the algorithm used to encrypt {@code key} is unkown
	 * @throws NoSuchPaddingException
	 *              if the padding scheme specified in the decryption algorithm
	 *              is unkown
	 * @throws InvalidKeySpecException
	 *              if the decryption key based on {@code password} cannot be
	 *              generated
	 * @throws InvalidKeyException
	 *              if the decryption key based on {@code password} cannot be
	 *              used to decrypt {@code key}
	 * @throws InvalidAlgorithmParameterException
	 *              if decryption algorithm parameters are somehow faulty
	 */
	protected static PKCS8EncodedKeySpec generateKeySpec(char[] password, byte[] key) throws IOException,
			NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeySpecException, InvalidKeyException,
			InvalidAlgorithmParameterException {

		if (password == null) {
			return new PKCS8EncodedKeySpec(key);
		}

		EncryptedPrivateKeyInfo encryptedPrivateKeyInfo = new EncryptedPrivateKeyInfo(key);
		SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(encryptedPrivateKeyInfo.getAlgName());
		PBEKeySpec pbeKeySpec = new PBEKeySpec(password);
		SecretKey pbeKey = keyFactory.generateSecret(pbeKeySpec);

		Cipher cipher = Cipher.getInstance(encryptedPrivateKeyInfo.getAlgName());
		cipher.init(Cipher.DECRYPT_MODE, pbeKey, encryptedPrivateKeyInfo.getAlgParameters());

		return encryptedPrivateKeyInfo.getKeySpec(cipher);
	}

	/**
	 * Generates a new {@link KeyStore}.
	 *
	 * @param certChain
	 *             a X.509 certificate chain
	 * @param key
	 *             a PKCS#8 private key
	 * @param keyPasswordChars
	 *             the password of the {@code keyFile}. {@code null} if it's
	 *             not password-protected.
	 * @return generated {@link KeyStore}.
	 */
	static KeyStore buildKeyStore(X509Certificate[] certChain, PrivateKey key, char[] keyPasswordChars)
			throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		KeyStore ks = KeyStore.getInstance("JKS");
		ks.load(null, null);
		ks.setKeyEntry("key", key, keyPasswordChars, certChain);
		return ks;
	}

	static PrivateKey toPrivateKey(File keyFile, String keyPassword) throws NoSuchAlgorithmException,
			NoSuchPaddingException, InvalidKeySpecException, InvalidAlgorithmParameterException, KeyException,
			IOException {
		if (keyFile == null) {
			return null;
		}
		return getPrivateKeyFromByteBuffer(PemReader.readPrivateKey(keyFile), keyPassword);
	}

	static PrivateKey toPrivateKey(InputStream keyInputStream, String keyPassword) throws NoSuchAlgorithmException,
			NoSuchPaddingException, InvalidKeySpecException, InvalidAlgorithmParameterException, KeyException,
			IOException {
		if (keyInputStream == null) {
			return null;
		}
		return getPrivateKeyFromByteBuffer(PemReader.readPrivateKey(keyInputStream), keyPassword);
	}

	private static PrivateKey getPrivateKeyFromByteBuffer(byte[] encodedKey, String keyPassword)
			throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeySpecException,
			InvalidAlgorithmParameterException, KeyException, IOException {

		PKCS8EncodedKeySpec encodedKeySpec = generateKeySpec(keyPassword == null ? null : keyPassword.toCharArray(),
				encodedKey);
		try {
			return KeyFactory.getInstance("RSA").generatePrivate(encodedKeySpec);
		} catch (InvalidKeySpecException ignore) {
			try {
				return KeyFactory.getInstance("DSA").generatePrivate(encodedKeySpec);
			} catch (InvalidKeySpecException ignore2) {
				try {
					return KeyFactory.getInstance("EC").generatePrivate(encodedKeySpec);
				} catch (InvalidKeySpecException e) {
					throw new InvalidKeySpecException("Neither RSA, DSA nor EC worked", e);
				}
			}
		}
	}
	
	public SslHandler getSslHandler() {
		return sslHandler;
	}

	/**
	 * Build a {@link TrustManagerFactory} from a certificate chain file.
	 * 
	 * @param certChainFile
	 *             The certificate file to build from.
	 * @param trustManagerFactory
	 *             The existing {@link TrustManagerFactory} that will be used
	 *             if not {@code null}.
	 * @return A {@link TrustManagerFactory} which contains the certificates in
	 *         {@code certChainFile}
	 */
	@Deprecated
	protected static TrustManagerFactory buildTrustManagerFactory(File certChainFile,
			TrustManagerFactory trustManagerFactory) throws NoSuchAlgorithmException, CertificateException,
			KeyStoreException, IOException {
		X509Certificate[] x509Certs = toX509Certificates(certChainFile);

		return buildTrustManagerFactory(x509Certs, trustManagerFactory);
	}

	static X509Certificate[] toX509Certificates(File file) throws CertificateException {
		if (file == null) {
			return null;
		}
		return getCertificatesFromBuffers(PemReader.readCertificates(file));
	}

	static X509Certificate[] toX509Certificates(InputStream in) throws CertificateException {
		if (in == null) {
			return null;
		}
		return getCertificatesFromBuffers(PemReader.readCertificates(in));
	}

	private static X509Certificate[] getCertificatesFromBuffers(byte[][] certs) throws CertificateException {
		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		X509Certificate[] x509Certs = new X509Certificate[certs.length];

		for (int i = 0; i < certs.length; i++) {
			x509Certs[i] = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certs[i]));
		}
		return x509Certs;
	}

	static TrustManagerFactory buildTrustManagerFactory(X509Certificate[] certCollection,
			TrustManagerFactory trustManagerFactory) throws NoSuchAlgorithmException, CertificateException,
			KeyStoreException, IOException {
		KeyStore ks = KeyStore.getInstance("JKS");
		ks.load(null, null);

		int i = 1;
		for (X509Certificate cert : certCollection) {
			String alias = Integer.toString(i);
			ks.setCertificateEntry(alias, cert);
			i++;
		}

		// Set up trust manager factory to use our key store.
		if (trustManagerFactory == null) {
			trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		}
		trustManagerFactory.init(ks);

		return trustManagerFactory;
	}

	static PrivateKey toPrivateKeyInternal(File keyFile, String keyPassword) throws SSLException {
		try {
			return toPrivateKey(keyFile, keyPassword);
		} catch (Exception e) {
			throw new SSLException(e);
		}
	}

	static X509Certificate[] toX509CertificatesInternal(File file) throws SSLException {
		try {
			return toX509Certificates(file);
		} catch (CertificateException e) {
			throw new SSLException(e);
		}
	}

	static KeyManagerFactory buildKeyManagerFactory(X509Certificate[] certChain, PrivateKey key, String keyPassword,
			KeyManagerFactory kmf) throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException,
			CertificateException, IOException {
		String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
		if (algorithm == null) {
			algorithm = "SunX509";
		}
		return buildKeyManagerFactory(certChain, algorithm, key, keyPassword, kmf);
	}

	static KeyManagerFactory buildKeyManagerFactory(X509Certificate[] certChainFile, String keyAlgorithm,
			PrivateKey key, String keyPassword, KeyManagerFactory kmf) throws KeyStoreException,
			NoSuchAlgorithmException, IOException, CertificateException, UnrecoverableKeyException {
		char[] keyPasswordChars = keyPassword == null ? "".toCharArray() : keyPassword.toCharArray();
		KeyStore ks = buildKeyStore(certChainFile, key, keyPasswordChars);
		// Set up key manager factory to use our key store
		if (kmf == null) {
			kmf = KeyManagerFactory.getInstance(keyAlgorithm);
		}
		kmf.init(ks, keyPasswordChars);

		return kmf;
	}

	static KeyManagerFactory buildDefaultKeyManagerFactory() throws NoSuchAlgorithmException, KeyStoreException,
			UnrecoverableKeyException {
		KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		keyManagerFactory.init(null, null);
		return keyManagerFactory;
	}
}
