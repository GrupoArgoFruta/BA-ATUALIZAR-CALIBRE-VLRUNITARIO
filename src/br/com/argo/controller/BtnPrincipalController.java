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

public class BtnPrincipalController implements AcaoRotinaJava {
    @Override
    public void doAction(ContextoAcao ctx) throws Exception {
        Registro[] linhas = ctx.getLinhas();
        StringBuilder mensagemSucesso = new StringBuilder();
        ImpostosHelpper impostosHelper = new ImpostosHelpper();

        try {
            BigDecimal paramVlrunitario = BigDecimal.valueOf((Double) ctx.getParam("VLRUNIT"));

            // Parâmetro CALIBRE (String) — usuário digita o calibre que quer atualizar
            String paramCalibre = ctx.getParam("AD_CALIBRE") != null
                    ? ctx.getParam("AD_CALIBRE").toString().trim()
                    : null;

            // Iniciando a construção da mensagem de sucesso com a tabela
            mensagemSucesso.append("<!DOCTYPE html>").append("<html>").append("<body>")
                    .append("<div style='text-align: center;'>")
                    .append("<img src='https://argofruta.com/wp-content/uploads/2021/05/Logo-text-green.png' style='width:120px; height:90px;'>")
                    .append("</div>")
                    .append("<div style='display: flex; align-items: center; justify-content: center;'>")
                    .append("<img src='https://cdn-icons-png.flaticon.com/256/189/189677.png' style='width:23px; height:23px; margin-right:5px;'>")
                    .append("<p style='color:#274135; font-family:verdana; font-size:15px; margin: 0;'><b>Atualização de Valores Unitários</b></p>")
                    .append("</div>")
                    .append("<p style='font-family:courier; color:#274135;'>Produtos processados e valores unitários foram: <br><br>");

            int processados = 0;
            int ignorados = 0;

            // Itera sobre cada registro selecionado
            for (Registro registro : linhas) {

                BigDecimal nuNota = (BigDecimal) registro.getCampo("NUNOTA");
                BigDecimal seque = (BigDecimal) registro.getCampo("SEQUENCIA");
                BigDecimal codpro = (BigDecimal) registro.getCampo("CODPROD");
                BigDecimal qtdNeg = (BigDecimal) registro.getCampo("QTDNEG");

                // Filtro por calibre: se informou calibre, só atualiza itens daquele calibre
                if (paramCalibre != null && !paramCalibre.isEmpty()) {
                    Object calibreItem = registro.getCampo("AD_CALIBRE");
                    String calibreStr = calibreItem != null ? calibreItem.toString().trim() : "";

                    if (!paramCalibre.equals(calibreStr)) {
                        ignorados++;
                        continue;
                    }
                }

                atualizaCampos(nuNota, paramVlrunitario, seque, qtdNeg);
                impostosHelper.setForcarRecalculo(true);
                impostosHelper.carregarNota(nuNota);
                impostosHelper.calcularImpostos(nuNota);
                impostosHelper.totalizarNota(nuNota);
                impostosHelper.salvarNota();

                processados++;

                // Adiciona a mensagem de sucesso
                mensagemSucesso.append(codpro).append("  -- ").append("Valor Unitário: ")
                        .append(String.format("%.2f", paramVlrunitario)).append("<br>");
            }

            // Resumo do filtro
            if (paramCalibre != null && !paramCalibre.isEmpty()) {
                mensagemSucesso.append("<br><b>Calibre: ").append(paramCalibre).append("</b><br>");
                mensagemSucesso.append("Atualizados: ").append(processados)
                        .append(" | Ignorados: ").append(ignorados).append("<br>");
            }

            // Finaliza a mensagem de sucesso com a formatação HTML
            mensagemSucesso.append("</b></p>").append("</body>").append("</html>");

            // Define a mensagem de retorno
            ctx.setMensagemRetorno(mensagemSucesso.toString());

        } catch (Exception e) {
            ctx.mostraErro(e.getMessage());
        }
    }
    public void atualizaCampos(BigDecimal nota, BigDecimal vlrunitario, BigDecimal seq,BigDecimal qtdNeg) throws MGEModelException {
        // TODO Auto-generated method stub

        JapeSession.SessionHandle hnd = null;
        try {
            hnd = JapeSession.open();
            // Atualiza o valor unitário e o valor total
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
    public void AtualizaeVlrUnitario(BigDecimal nota, Double vlrunitario, BigDecimal seq, ContextoAcao ctx) throws Exception {
        JapeSession.SessionHandle hnd = JapeSession.open();
        hnd.setFindersMaxRows(-1);
        EntityFacade entity = EntityFacadeFactory.getDWFFacade();
        JdbcWrapper jdbc = entity.getJdbcWrapper();
        jdbc.openSession();
        ImpostosHelpper impostosHelper = new ImpostosHelpper();
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