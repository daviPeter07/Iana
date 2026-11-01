# IANA

Assistente pessoal estilo Jarvis para Android, em Kotlin. Você fala, ela entende, executa e responde em voz alta.

## O que já funciona

| Comando (exemplos) | O que faz |
|---|---|
| “notícias”, “notícias sobre futebol”, “manchetes do Flamengo” | Lê as 5 principais manchetes do Google Notícias (RSS, grátis e sem chave de API) |
| “minha agenda”, “agenda de amanhã”, “tenho algo na sexta?”, “agenda da semana” | Lê seus compromissos da agenda do celular |
| “qual meu próximo compromisso?” | Diz o próximo evento |
| “marca dentista sexta às 10h”, “me lembra de ligar pro contador dia 15 às 3 da tarde”, “marca almoço 3 de dezembro ao meio-dia por uma hora e meia” | Cria o evento na sua agenda (sincroniza com o Google Agenda) |
| “que horas são?”, “que dia é hoje?”, “ajuda” | O básico |

Também dá para digitar o comando na caixa de texto, o que é útil no emulador.

## Como rodar no celular

1. Instale o [Android Studio](https://developer.android.com/studio) e abra esta pasta.
2. No celular, ative **Opções do desenvolvedor**: Configurações › Sobre o telefone › toque 7 vezes em “Número da versão”. Depois ative **Depuração USB**.
3. Conecte o celular no USB, escolha-o na barra do Android Studio e clique em **Run ▶**.

Requisitos: Android 10 ou mais novo, com o app Google instalado (ele fornece o reconhecimento de voz).

Para a voz ficar boa e funcionar sem internet:
- **Reconhecimento offline:** Configurações › Google › Pesquisa, Assistente e voz › Voz › Reconhecimento de fala offline › baixe **Português (Brasil)**.
- **Voz da IANA:** Configurações › Acessibilidade › Saída de texto para voz › mecanismo Google › idioma Português (Brasil). Ali dá para escolher outras vozes.

## Como o código está organizado

```
app/src/main/java/com/davipeter/iana/
├── assistant/   ← o "cérebro", Kotlin puro (sem Android)
│   ├── CommandParser.kt   frase → Command (regras e palavras-chave)
│   ├── DateTimeParser.kt  "sexta às 3 da tarde" → data/hora
│   ├── Assistant.kt       roteador: executa o Command e monta a resposta
│   └── PtBr.kt            datas e horas faladas em português
├── data/
│   ├── news/      Google Notícias via RSS
│   └── calendar/  agenda do celular (CalendarContract)
├── voice/       SpeechListener (ouvir) e Speaker (falar)
├── ui/          tela em Jetpack Compose + MainViewModel
└── IanaApp.kt   monta as dependências (sem framework de DI)
```

A interpretação fica atrás da interface `Brain`. Hoje ela é feita por regras (`RuleBasedBrain`). Para usar IA no futuro, basta criar outra implementação de `Brain` e trocá-la em `IanaApp.kt`; os comandos continuam os mesmos.

## Testes

A pasta `assistant/` e a camada de dados têm testes de unidade (JUnit), que rodam na JVM sem emulador:

```bash
./gradlew test
```

## Próximos passos

- [ ] **Widget** com as manchetes, o próximo compromisso e um botão “falar com a IANA” (Jetpack Glance)
- [ ] **Palavra de ativação “Jarvis”** em segundo plano (Porcupine + serviço em primeiro plano)
- [ ] **Resumo falado da manhã** com notícias, agenda e DMs pendentes (AlarmManager + leitor de notificações)
