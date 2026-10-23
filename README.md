# BLACK Android — preparador visual

[Baixar o APK](https://github.com/impacktmkt/black-android-local/releases/download/v1.5.0-local.3/BLACK-Preparador-1.5.0.apk)

## Instalar e jogar

1. No Android, baixe o APK e confirme a instalação. Se o sistema pedir, permita instalar apps dessa fonte.
2. Abra o ícone **BLACK** e toque em **Preparar jogo**.
3. Aguarde o download de **2,22 GB** e a verificação dos dados. O app abre o jogo ao concluir.

Depois, basta abrir o ícone BLACK. Não é necessário baixar ZIP, extrair arquivos, selecionar pastas
ou conceder acesso a todos os arquivos. O botão **Import game** permanece como alternativa.
Mantenha o app aberto durante a preparação. Se a conexão cair ou você sair do app,
toque em **Continuar preparação** para retomar. Reserve cerca de **3 GB livres** para preparar.

## Requisitos e validação

Android 10 ou mais recente, ARM64, Vulkan 1.1 e 4 GB de RAM, conforme o port original.
Compilação, assinatura APK v3 e alinhamento de bibliotecas a 16 KB são verificados no PC.
Os testes JVM cobrem o validador original e o preparador, incluindo retomada entre partes.
A transferência pública completa também é verificada pelo código de download do app, no JVM do PC.
**Não houve teste em celular.** A execução e a compatibilidade gráfica dependem do aparelho.

## Arquivos do jogo

Os dados ficam separados do APK e são obtidos da [Release de dados](https://github.com/impacktmkt/black-android-local/releases/tag/game-data-v1).
A divisão em duas partes atende ao limite por arquivo do GitHub Releases; o app reúne tudo automaticamente.
As partes são gravadas diretamente na pasta específica do app, sem criar cópias públicas em Downloads.
Somente após tamanho, cabeçalho Xbox e SHA-256 corretos a imagem fica disponível para o motor.

- Tamanho final: 2.222.194.688 bytes.
- SHA-256: `7fede48e38662c3702fc90b70b97afcafc6745f2795ce730b79c69c7ba9b2995`.
- Pacote Android: `com.local.black.downloads`.
- Versão: `1.5.0-local.3`, código 15003.

O pacote e a assinatura são próprios e permitem coexistir com o APK oficial.
O atualizador oficial está desativado. Desinstalar esta variante apaga seus dados e saves.

## Origem e compilação

Baseado no código Java e recursos do port [RanggaCasper/Black-Android](https://github.com/RanggaCasper/Black-Android),
tag v1.5.0, commit `f3754d6985a8c35204e7df071546e586b10f1806`.
O ícone e as três bibliotecas nativas ARM64 foram preservados do APK original, byte a byte.
O motor nativo não foi recompilado. Veja [BUILD.md](BUILD.md) para reproduzir a camada Android.

Créditos: RanggaCasper e contribuidores; [XboxXBE-Recomp](https://github.com/TGP482/XboxXBE-Recomp);
[SDL](https://github.com/libsdl-org/SDL). Esta é uma variante independente, não o projeto oficial.
A licença MIT do código do port não concede por si só direitos sobre o jogo, marcas ou seus dados.
