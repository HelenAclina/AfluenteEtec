# AFLUENTE

Aplicativo Android para monitorar e reduzir o consumo doméstico de água e energia elétrica.
Trabalho de Conclusão de Curso do Técnico em Desenvolvimento de Sistemas (ETEC – modalidade EaD).

**Equipe:** Camila Morais Melo · Eduardo Sassi Ramos · Helen Vitoria Silva Aclina · Tony Bonilha Vieira
**Orientador:** Prof. Analder Magalhães Honorio

## Funcionalidades

- Cadastro em cinco etapas e login por e-mail e senha (Firebase Authentication)
- Registro, edição, exclusão e histórico de leituras de água (m³) e energia (kWh)
- Cálculo do gasto a partir das tarifas informadas
- Painel com consumo do mês, gasto estimado, metas e gráfico dos últimos seis meses
- Análise por recurso e comparação entre dois meses
- Metas mensais e alertas automáticos (meta ultrapassada e aumento de 30% ou mais)
- Relatórios mensais e anuais com exportação em CSV
- Perfil com foto, tarifas, tema escuro e troca de e-mail e senha

## Instalar no celular (sem Android Studio)

1. Abra a aba **[Releases](../../releases)** deste repositório no celular Android.
2. Na versão mais recente, toque em **AFLUENTE.apk** para baixar.
3. Abra o arquivo baixado e toque em **Instalar**. Se o Android pedir, permita a instalação de apps desta fonte.

Requisitos: Android 7.1 ou superior e internet para criar a conta e entrar.

O APK é gerado automaticamente pelo GitHub Actions a cada atualização do código (aba **Actions**).

## Rodar pelo Android Studio

1. **File → New → Project from Version Control** e cole o endereço deste repositório.
2. Aguarde o Gradle sincronizar (na primeira vez, ele baixa as dependências).
3. Conecte um celular com depuração USB ativada, ou crie um emulador, e clique em **Run ▶**.

Requisitos: Android Studio atualizado e JDK 21 (o Android Studio já inclui um).

## Testes

```bash
./gradlew testDebugUnitTest          # testes locais (regras de consumo e custo)
./gradlew connectedDebugAndroidTest  # testes de integridade do banco (precisa de celular ou emulador)
```

## Estrutura do código

| Pacote | Conteúdo |
|---|---|
| `ui` | Telas (Activities e Fragments), adaptadores de lista e gráficos |
| `service` | Regras de negócio: validação, cálculo de custo, alertas, carregamento em segundo plano |
| `data` | Banco SQLite (`DatabaseHelper`) e DAOs |
| `model` | Objetos de dados |

Detalhes técnicos e decisões de implementação: [REVISAO_TECNICA.md](REVISAO_TECNICA.md).
