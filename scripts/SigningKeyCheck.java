import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HexFormat;

// Adapted from Oraculum SigningKeyCheck.java at 3f98a0f506f7c372ed1743f1201ad23136c8c639.
// Local password file replaces Console.readPassword and offline-backup custody.
class SigningKeyCheck {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected JKS/password-file/alias paths");
        char[] password = Files.readString(Path.of(args[1]), StandardCharsets.UTF_8).toCharArray();
        if (password.length < 2 || password[password.length - 1] != '\n')
            throw new IllegalArgumentException("Expected one LF-terminated password line");
        char[] credential = Arrays.copyOf(password, password.length - 1);
        Arrays.fill(password, '\0');
        try {
            KeyStore store = KeyStore.getInstance("JKS");
            try (var input = Files.newInputStream(Path.of(args[0]))) { store.load(input, credential); }
            if (store.size() != 1 || !store.isKeyEntry(args[2]))
                throw new IllegalStateException("Expected one dedicated release alias");
            var key = store.getKey(args[2], credential);
            if (!(key instanceof PrivateKey)) throw new IllegalStateException("Private key cannot be unlocked");
            if (!(store.getCertificate(args[2]) instanceof X509Certificate certificate))
                throw new IllegalStateException("Expected X.509 certificate");
            certificate.checkValidity();
            if (!(certificate.getPublicKey() instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < 3072)
                throw new IllegalStateException("Expected RSA key with at least 3072 bits");
            if (certificate.getSubjectX500Principal().getName().contains("Android Debug"))
                throw new IllegalStateException("Debug signing identity rejected");
            byte[] challenge = "Dicta local signing identity verification".getBytes(StandardCharsets.UTF_8);
            Signature proof = Signature.getInstance("SHA256withRSA");
            proof.initSign((PrivateKey) key); proof.update(challenge);
            byte[] signature = proof.sign();
            proof.initVerify(certificate); proof.update(challenge);
            if (!proof.verify(signature)) throw new IllegalStateException("Private key proof failed");
            System.out.println(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded())));
        } finally {
            Arrays.fill(credential, '\0');
        }
    }
}
