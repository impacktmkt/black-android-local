# Reproduzir a compilação Android

Fontes Java e recursos derivados do port oficial v1.5.0, commit f3754d6985a8c35204e7df071546e586b10f1806.
O motor nativo foi preservado byte a byte do APK oficial fornecido; não foi recompilado.

1. Windows, Git e Python 3.12. Execute `python toolchain/setup.py` para preparar JDK 17,
   Android Platform 34, Build Tools 35 e platform-tools no diretório local.
2. Coloque o APK oficial Black-1.5.0.apk em private-input/.
   SHA-256 exigido: 102340f238a97ef18a28c9a2e0e2ec47c3e7a982ab1b3845cd65305cad07d28a.
   O script extrai somente as bibliotecas nativas; nunca coloca a ISO no APK.
3. Para executar também o teste do validador com a imagem completa, forneça sua cópia em
   private-input/BLACK.iso. O build não incorpora esse arquivo.
4. Execute `python build-public.py`. O script recusa sobrescrever um APK existente.
   Ele usa javac, aapt2, D8, zipalign (16 KB) e apksigner; não utiliza Gradle/NDK.
5. O resultado é BLACK-Preparador-1.5.0.apk. Uma nova chave é gerada apenas se não houver
   private-signing/black-local.p12; ela não permite atualizar os binários publicados aqui.
   Preserve sua chave local para suas próprias atualizações e nunca a publique.

O app usa com.local.black.downloads e mantém com.racas.black.BlackActivity como classe JNI.
O launcher é PreparationActivity. Não solicita acesso ao armazenamento compartilhado.
Atualizações oficiais são desativadas. O endpoint de dados fica em values/download.xml.

Testes JVM cobrem o validador original e transferências simuladas, incluindo retomada entre partes.
Eles não executam Android, a interface nativa nem o jogo. Nenhum teste em celular foi realizado.

Motor e port: https://github.com/RanggaCasper/Black-Android
SDL: https://github.com/libsdl-org/SDL/tree/release-3.4.16
