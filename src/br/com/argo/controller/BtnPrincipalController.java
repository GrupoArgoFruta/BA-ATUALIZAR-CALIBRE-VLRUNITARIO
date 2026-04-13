package br.com.argo.controller;

import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava;
import br.com.sankhya.extensions.actionbutton.ContextoAcao;
import br.com.sankhya.extensions.actionbutton.Registro;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.modelcore.MGEModelException;
import br.com.sankhya.modelcore.comercial.impostos.ImpostosHelpper;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BtnPrincipalController — Atualização de Valores Unitários por Calibre
 *
 * Parâmetros da ação (configurar no Sankhya):
 *   - CALIBRE1 (Texto) + VLRUNIT1 (Decimal) — Par 1
 *   - CALIBRE2 (Texto) + VLRUNIT2 (Decimal) — Par 2
 *   - CALIBRE3 (Texto) + VLRUNIT3 (Decimal) — Par 3
 *   - CALIBRE4 (Texto) + VLRUNIT4 (Decimal) — Par 4
 *   - CALIBRE5 (Texto) + VLRUNIT5 (Decimal) — Par 5
 *
 * Fluxo:
 *   1) Usuário seleciona os itens na grid
 *   2) Popup abre com 5 pares calibre/valor — o calibre pode vir preenchido via SQL default
 *   3) Usuário preenche o valor unitário para cada calibre desejado
 *   4) O sistema aplica o valor SOMENTE nos itens cujo AD_CALIBRE bate com o calibre informado
 *   5) Pares vazios são ignorados
 *
 * @author Natan — Grupo Argo
 * @since 2026-04-13
 */
public class BtnPrincipalController implements AcaoRotinaJava {

    /** Quantidade máxima de pares calibre/valor suportados */
    private static final int MAX_PARES = 5;

    @Override
    public void doAction(ContextoAcao ctx) throws Exception {
        Registro[] linhas = ctx.getLinhas();
        StringBuilder mensagemSucesso = new StringBuilder();
        ImpostosHelpper impostosHelper = new ImpostosHelpper();

        try {
            // ── 1. Ler os pares CALIBRE/VLRUNIT dos parâmetros ───────────
            Map<String, BigDecimal> mapaCalibreValor = new LinkedHashMap<>();

            for (int i = 1; i <= MAX_PARES; i++) {
                String calibre = getParamString(ctx, "CALIBRE" + i);
                BigDecimal valor = getParamBigDecimal(ctx, "VLRUNIT" + i);

                if (calibre != null && !calibre.isEmpty() && valor != null) {
                    mapaCalibreValor.put(calibre, valor);
                }
            }

            if (mapaCalibreValor.isEmpty()) {
                ctx.mostraErro("Nenhum par calibre/valor foi preenchido. Informe ao menos um calibre e seu valor unitário.");
                return;
            }

            // ── 2. Montar cabeçalho HTML ─────────────────────────────────
            mensagemSucesso.append("<!DOCTYPE html>").append("<html>").append("<body>")
                    .append("<div style='text-align: center;'>")
                    .append("<img src='https://argofruta.com/wp-content/uploads/2021/05/Logo-text-green.png' style='width:120px; height:90px;'>")
                    .append("</div>")
                    .append("<div style='display: flex; align-items: center; justify-content: center;'>")
                    .append("<img src='https://cdn-icons-png.flaticon.com/256/189/189677.png' style='width:23px; height:23px; margin-right:5px;'>")
                    .append("<p style='color:#274135; font-family:verdana; font-size:15px; margin: 0;'>")
                    .append("<b>Atualização de Valores Unitários por Calibre</b></p>")
                    .append("</div>");

            // Tabela de calibres aplicados
            mensagemSucesso.append("<div style='margin:10px auto; max-width:400px;'>")
                    .append("<table style='width:100%; border-collapse:collapse; font-family:verdana; font-size:12px; color:#274135;'>")
                    .append("<tr style='background:#274135; color:white;'>")
                    .append("<th style='padding:5px;'>Calibre</th>")
                    .append("<th style='padding:5px;'>Vlr Unitário</th></tr>");

            for (Map.Entry<String, BigDecimal> entry : mapaCalibreValor.entrySet()) {
                mensagemSucesso.append("<tr>")
                        .append("<td style='padding:4px; text-align:center; border:1px solid #ddd;'>").append(entry.getKey()).append("</td>")
                        .append("<td style='padding:4px; text-align:right; border:1px solid #ddd;'>").append(String.format("%.2f", entry.getValue())).append("</td>")
                        .append("</tr>");
            }
            mensagemSucesso.append("</table></div>");

            mensagemSucesso.append("<p style='font-family:courier; color:#274135;'>Itens processados:<br><br>");

            // ── 3. Iterar itens e aplicar valor por calibre ──────────────
            int processados = 0;
            int ignorados = 0;
            // Controla quais notas precisam recalcular impostos
            Map<BigDecimal, Boolean> notasProcessadas = new LinkedHashMap<>();

            for (Registro registro : linhas) {
                BigDecimal nuNota = (BigDecimal) registro.getCampo("NUNOTA");
                BigDecimal seque  = (BigDecimal) registro.getCampo("SEQUENCIA");
                BigDecimal codpro = (BigDecimal) registro.getCampo("CODPROD");
                BigDecimal qtdNeg = (BigDecimal) registro.getCampo("QTDNEG");

                // Lê o calibre do item
                Object calibreObj = registro.getCampo("AD_CALIBRE");
                String calibreItem = calibreObj != null ? calibreObj.toString().trim() : "";

                // Verifica se tem valor definido para esse calibre
                BigDecimal vlrUnit = mapaCalibreValor.get(calibreItem);

                if (vlrUnit == null) {
                    ignorados++;
                    continue; // Calibre do item não está nos parâmetros — pula
                }

                // Atualiza o item
                atualizaCampos(nuNota, vlrUnit, seque, qtdNeg);
                notasProcessadas.put(nuNota, true);
                processados++;

                mensagemSucesso.append(codpro)
                        .append(" [Cal: ").append(calibreItem).append("]")
                        .append("  -- Vlr Unit: ").append(String.format("%.2f", vlrUnit))
                        .append("<br>");
            }

            // ── 4. Recalcular impostos (uma vez por nota) ────────────────
            for (BigDecimal nuNota : notasProcessadas.keySet()) {
                impostosHelper.setForcarRecalculo(true);
                impostosHelper.carregarNota(nuNota);
                impostosHelper.calcularImpostos(nuNota);
                impostosHelper.totalizarNota(nuNota);
                impostosHelper.salvarNota();
            }

            // ── 5. Resumo final ──────────────────────────────────────────
            mensagemSucesso.append("<br><b>Atualizados: ").append(processados)
                    .append(" | Ignorados: ").append(ignorados).append("</b>");

            mensagemSucesso.append("</p>").append("</body>").append("</html>");
            ctx.setMensagemRetorno(mensagemSucesso.toString());

        } catch (Exception e) {
            ctx.mostraErro(e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MÉTODOS AUXILIARES
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Atualiza VLRUNIT e VLRTOT do item via JapeFactory.
     */
    public void atualizaCampos(BigDecimal nota, BigDecimal vlrunitario, BigDecimal seq, BigDecimal qtdNeg) throws MGEModelException {
        JapeSession.SessionHandle hnd = null;
        try {
            hnd = JapeSession.open();
            BigDecimal vlrTotal = vlrunitario.multiply(qtdNeg);
            JapeFactory.dao("ItemNota").prepareToUpdateByPK(nota, seq)
                    .set("VLRUNIT", vlrunitario)
                    .set("VLRTOT", vlrTotal)
                    .update();
        } catch (Exception e) {
            throw new MGEModelException("Erro ao atualizar o valor unitário: " + e.getMessage(), e);
        } finally {
            JapeSession.close(hnd);
        }
    }

    /**
     * Lê parâmetro como String (null-safe).
     */
    private String getParamString(ContextoAcao ctx, String nome) {
        try {
            Object val = ctx.getParam(nome);
            if (val == null) return null;
            String str = val.toString().trim();
            return str.isEmpty() ? null : str;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Lê parâmetro como BigDecimal (null-safe).
     */
    private BigDecimal getParamBigDecimal(ContextoAcao ctx, String nome) {
        try {
            Object val = ctx.getParam(nome);
            if (val == null) return null;
            if (val instanceof BigDecimal) return (BigDecimal) val;
            if (val instanceof Double) return BigDecimal.valueOf((Double) val);
            return new BigDecimal(val.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    public void AtualizaeVlrUnitario(BigDecimal nota, Double vlrunitario, BigDecimal seq, ContextoAcao ctx) throws Exception {
        JapeSession.SessionHandle hnd = JapeSession.open();
        hnd.setFindersMaxRows(-1);
        EntityFacade entity = EntityFacadeFactory.getDWFFacade();
        JdbcWrapper jdbc = entity.getJdbcWrapper();
        jdbc.openSession();
        try {
            NativeSql sql = new NativeSql(jdbc);
            sql.appendSql("UPDATE TGFITE SET VLRUNIT = :VLRUNIT WHERE NUNOTA = :NUNOTA AND SEQUENCIA = :SEQUENCIA");
            sql.setNamedParameter("VLRUNIT", vlrunitario);
            sql.setNamedParameter("NUNOTA", nota);
            sql.setNamedParameter("SEQUENCIA", seq);
            sql.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Erro ao executar AtualizaeVlrUnitario: " + e.getMessage());
        } finally {
            JdbcWrapper.closeSession(jdbc);
            JapeSession.close(hnd);
        }
    }
}