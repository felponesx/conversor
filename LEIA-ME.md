# Conversor Nuvem 3.0

App Android que transforma código em APK.

- **HTML**: motor nativo. Monta, alinha e assina o APK dentro do celular, sem GitHub e sem internet.
- **Kotlin/Java, Flutter e React Native**: compilam pelo GitHub Actions (como antes).

## Como o motor nativo funciona
1. O app guarda um APK modelo (`assets/engine/shell.apk`): uma casca com WebView que abre `assets/www/index.html`.
2. Ao compilar, o motor troca o site dentro do modelo, muda o nome, o ID do pacote, o versionCode e os ícones.
3. Alinha o ZIP e assina com o esquema v2, usando uma chave guardada no AndroidKeyStore.
4. Abre o instalador do Android (PackageInstaller).

Código do motor: `app/src/main/java/com/felipe/conversornuvem/engine/`.

## Gerar o APK pelo Git
1. Crie um repositório no GitHub e suba o conteúdo deste zip na raiz (settings.gradle.kts na raiz).
2. A Action "Compilar Conversor Nuvem" roda sozinha a cada envio, ou em Actions > Run workflow.
3. O APK aparece em Releases (conversor-nuvem.apk) e também em Artifacts.
4. Desinstale a versão antiga antes de instalar (a assinatura muda a cada build do GitHub).

## Primeira vez
- Permita instalar apps deste aplicativo quando o Android pedir.
- Para Kotlin, Flutter e React Native: em Configurações, coloque o token (escopos repo e workflow),
  o usuário e o repositório e toque em "Salvar e instalar o compilador".

## Limites do app gerado (WebView)
- GPS e JavaScript só funcionam com o app aberto na tela.
- Service workers e notificações push não são suportados.
- O ícone fica cortado nas bordas pela forma do celular: deixe o desenho no centro.
- A chave de assinatura é perdida se você desinstalar o Conversor Nuvem.
