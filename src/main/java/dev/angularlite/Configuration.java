package dev.angularlite;

import com.intellij.openapi.options.Configurable;
import javax.swing.*;
import java.awt.*;

public final class Configuration implements Configurable {
    private JTextField node, runtimes;
    private JCheckBox automatic;
    public String getDisplayName() { return "Angular Lite"; }
    public JComponent createComponent() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 8));
        panel.add(new JLabel("Angular Lite 0.1.0"));
        JButton diagnostics = new JButton("Copier le diagnostic de navigation");
        diagnostics.addActionListener(event -> {
            com.intellij.openapi.ide.CopyPasteManager.getInstance().setContents(
                new java.awt.datatransfer.StringSelection(NavigationDiagnostics.report()));
            diagnostics.setText("Diagnostic copié");
        });
        panel.add(diagnostics);
        panel.add(new JLabel("Type .ts détecté : " + com.intellij.openapi.fileTypes.FileTypeManager.getInstance().getFileTypeByExtension("ts").getName()));
        panel.add(automatic = new JCheckBox("Préparer automatiquement les outils Angular (recommandé)"));
        panel.add(new JLabel("Node.js — uniquement si la préparation automatique est désactivée"));
        panel.add(node = new JTextField());
        panel.add(new JLabel("Language-server runtimes directory"));
        panel.add(runtimes = new JTextField());
        panel.add(new JLabel("Au premier démarrage, les outils sont téléchargés dans un cache privé. Aucun terminal nécessaire."));
        reset();
        JPanel outer = new JPanel(new BorderLayout()); outer.add(panel, BorderLayout.NORTH); return outer;
    }
    public boolean isModified() { return automatic.isSelected() != Settings.get().automatic || !node.getText().trim().equals(Settings.get().node) || !runtimes.getText().trim().equals(Settings.get().runtimes); }
    public void apply() { Settings.get().automatic = automatic.isSelected(); Settings.get().node = node.getText().trim(); Settings.get().runtimes = runtimes.getText().trim(); }
    public void reset() { automatic.setSelected(Settings.get().automatic); node.setText(Settings.get().node); runtimes.setText(Settings.get().runtimes); }
    public void disposeUIResources() { node = null; runtimes = null; automatic = null; }
}
