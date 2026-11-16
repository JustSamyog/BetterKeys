using System;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Threading;
using System.Windows.Forms;

namespace BetterKeysSetup
{
    static class Program
    {
        [STAThread]
        static int Main(string[] args)
        {
            string installDir = Environment.ExpandEnvironmentVariables(@"%LOCALAPPDATA%\Programs\BetterKeys");
            bool runAfter = true;
            bool silent = false;
            foreach (string a in args)
            {
                if (a.StartsWith("/dir=")) installDir = a.Substring(5);
                else if (a.StartsWith("/norun")) runAfter = false;
                else if (a.StartsWith("/silent")) silent = true;
            }

            if (silent)
            {
                try
                {
                    DoInstall(installDir, null);
                    return 0;
                }
                catch (Exception)
                {
                    return 1;
                }
            }

            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new SetupForm(installDir, runAfter));
            return 0;
        }

        public static string DoInstall(string installDir, Action<string> status)
        {
            KillRunningApp();

            Directory.CreateDirectory(installDir);

            string payload = Path.Combine(Path.GetTempPath(), "betterkeys_payload_" + ProcessId() + ".zip");
            try
            {
                if (status != null) status("Preparing files...");
                using (Stream src = Assembly.GetExecutingAssembly().GetManifestResourceStream("BKPayload"))
                using (FileStream dst = new FileStream(payload, FileMode.Create, FileAccess.Write))
                {
                    byte[] buf = new byte[81920];
                    int n;
                    while ((n = src.Read(buf, 0, buf.Length)) > 0) dst.Write(buf, 0, n);
                }

                if (status != null) status("Extracting files...");
                using (ZipArchive zip = ZipFile.OpenRead(payload))
                {
                    foreach (ZipArchiveEntry entry in zip.Entries)
                    {
                        string dest = Path.GetFullPath(Path.Combine(installDir, entry.FullName.Replace('/', Path.DirectorySeparatorChar)));
                        if (!dest.StartsWith(Path.GetFullPath(installDir), StringComparison.OrdinalIgnoreCase))
                            continue;
                        if (string.IsNullOrEmpty(entry.Name))
                        {
                            Directory.CreateDirectory(dest);
                            continue;
                        }
                        Directory.CreateDirectory(Path.GetDirectoryName(dest));
                        entry.ExtractToFile(dest, true);
                    }
                }
            }
            finally
            {
                try { if (File.Exists(payload)) File.Delete(payload); }
                catch (Exception) { }
            }

            string exePath = Path.Combine(installDir, "BetterKeys.exe");
            if (!File.Exists(exePath))
                throw new FileNotFoundException("BetterKeys.exe missing after install");

            if (status != null) status("Creating shortcuts...");
            CreateStartMenuShortcut(exePath, installDir);
            return exePath;
        }

        static void KillRunningApp()
        {
            try
            {
                System.Diagnostics.Process[] procs = System.Diagnostics.Process.GetProcessesByName("BetterKeys");
                foreach (System.Diagnostics.Process p in procs)
                {
                    try { p.Kill(); } catch (Exception) { }
                }
            }
            catch (Exception) { }
        }

        static void CreateStartMenuShortcut(string exePath, string workDir)
        {
            try
            {
                string programs = Environment.GetFolderPath(Environment.SpecialFolder.Programs);
                string lnkPath = Path.Combine(programs, "Better Keys.lnk");
                Type t = Type.GetTypeFromProgID("WScript.Shell");
                if (t == null) return;
                object sh = Activator.CreateInstance(t);
                object lnk = t.InvokeMember("CreateShortcut", BindingFlags.InvokeMethod, null, sh, new object[] { lnkPath });
                lnk.GetType().InvokeMember("TargetPath", BindingFlags.SetProperty, null, lnk, new object[] { exePath });
                lnk.GetType().InvokeMember("WorkingDirectory", BindingFlags.SetProperty, null, lnk, new object[] { workDir });
                lnk.GetType().InvokeMember("Description", BindingFlags.SetProperty, null, lnk, new object[] { "Better Keys - mechanical keyboard sounds" });
                lnk.GetType().InvokeMember("Save", BindingFlags.InvokeMethod, null, lnk, null);
            }
            catch (Exception) { }
        }

        static string ProcessId()
        {
            return System.Diagnostics.Process.GetCurrentProcess().Id.ToString();
        }
    }

    public class SetupForm : Form
    {
        private readonly string installDir;
        private readonly Panel installPanel;
        private readonly Panel donePanel;
        private readonly TextBox dirTxt;
        private readonly CheckBox runChk;
        private readonly CheckBox finishChk;
        private readonly Button installBtn;
        private readonly Button cancelBtn;
        private readonly Button finishBtn;
        private readonly Label statusLbl;
        private string installedExe;

        private static readonly Color Ink = Color.FromArgb(240, 240, 243);
        private static readonly Color Dim = Color.FromArgb(184, 185, 192);
        private static readonly Color Bg = Color.FromArgb(0x23, 0x23, 0x27);
        private static readonly Color Field = Color.FromArgb(0x1a, 0x1a, 0x1c);
        private static readonly Color Btn = Color.FromArgb(0x2b, 0x2b, 0x30);

        public SetupForm(string startDir, bool runAfter)
        {
            installDir = startDir;

            Text = "Better Keys Setup";
            ClientSize = new Size(470, 278);
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Bg;
            Font = new Font("Comic Sans MS", 9.5f);

            installPanel = new Panel();
            installPanel.Dock = DockStyle.Fill;

            Label title = MakeLabel("Better Keys Setup", 15.5f, true, Ink);
            title.Location = new Point(22, 16);
            installPanel.Controls.Add(title);

            Label info = MakeLabel("Installs the mechanical keyboard sound app.", 9f, false, Dim);
            info.Location = new Point(24, 50);
            installPanel.Controls.Add(info);

            Label dirLbl = MakeLabel("Install to:", 9f, false, Dim);
            dirLbl.Location = new Point(24, 84);
            installPanel.Controls.Add(dirLbl);

            dirTxt = new TextBox();
            dirTxt.Location = new Point(24, 106);
            dirTxt.Size = new Size(340, 24);
            dirTxt.BackColor = Field;
            dirTxt.ForeColor = Ink;
            dirTxt.BorderStyle = BorderStyle.FixedSingle;
            dirTxt.Font = new Font("Comic Sans MS", 9f);
            dirTxt.Text = installDir;
            installPanel.Controls.Add(dirTxt);

            Button browseBtn = MakeButton("Browse...", false);
            browseBtn.Location = new Point(370, 104);
            browseBtn.Click += BrowseBtn_Click;
            installPanel.Controls.Add(browseBtn);

            runChk = new CheckBox();
            runChk.Text = "Run Better Keys after setup";
            runChk.Location = new Point(24, 146);
            runChk.AutoSize = true;
            runChk.ForeColor = Ink;
            runChk.BackColor = Bg;
            runChk.Font = new Font("Comic Sans MS", 9.5f);
            runChk.Checked = runAfter;
            installPanel.Controls.Add(runChk);

            statusLbl = MakeLabel("", 8.5f, false, Dim);
            statusLbl.Location = new Point(24, 186);
            statusLbl.Size = new Size(300, 20);
            installPanel.Controls.Add(statusLbl);

            installBtn = MakeButton("Install", true);
            installBtn.Location = new Point(232, 222);
            installBtn.Click += InstallBtn_Click;
            installPanel.Controls.Add(installBtn);

            cancelBtn = MakeButton("Cancel", false);
            cancelBtn.Location = new Point(344, 222);
            cancelBtn.Click += delegate { Close(); };
            installPanel.Controls.Add(cancelBtn);

            donePanel = new Panel();
            donePanel.Dock = DockStyle.Fill;
            donePanel.Visible = false;

            Label doneTitle = MakeLabel("Setup finished.", 14f, true, Ink);
            doneTitle.Location = new Point(22, 26);
            donePanel.Controls.Add(doneTitle);

            Label doneSub = MakeLabel("Better Keys was installed successfully.", 9f, false, Dim);
            doneSub.Location = new Point(24, 62);
            donePanel.Controls.Add(doneSub);

            finishChk = new CheckBox();
            finishChk.Text = "Run Better Keys";
            finishChk.Location = new Point(24, 100);
            finishChk.AutoSize = true;
            finishChk.ForeColor = Ink;
            finishChk.BackColor = Bg;
            finishChk.Font = new Font("Comic Sans MS", 9.5f);
            finishChk.Checked = runAfter;
            donePanel.Controls.Add(finishChk);

            finishBtn = MakeButton("Finish", true);
            finishBtn.Location = new Point(344, 222);
            finishBtn.Click += FinishBtn_Click;
            donePanel.Controls.Add(finishBtn);

            Label footer = MakeLabel("Better Keys", 8.5f, false, Color.FromArgb(120, 120, 128));
            footer.Location = new Point(24, 244);
            donePanel.Controls.Add(footer);

            Controls.Add(installPanel);
            Controls.Add(donePanel);
        }

        private static Label MakeLabel(string text, float size, bool bold, Color color)
        {
            Label l = new Label();
            l.Text = text;
            l.Font = new Font("Comic Sans MS", size, bold ? FontStyle.Bold : FontStyle.Regular);
            l.ForeColor = color;
            l.BackColor = Color.Transparent;
            l.AutoSize = true;
            return l;
        }

        private static Button MakeButton(string text, bool accent)
        {
            Button b = new Button();
            b.Text = text;
            b.Size = new Size(100, 34);
            b.FlatStyle = FlatStyle.Flat;
            b.FlatAppearance.BorderSize = 0;
            b.Cursor = Cursors.Hand;
            b.Font = new Font("Comic Sans MS", 9.5f, FontStyle.Bold);
            if (accent)
            {
                b.BackColor = Ink;
                b.ForeColor = Color.FromArgb(0x11, 0x11, 0x13);
            }
            else
            {
                b.BackColor = Btn;
                b.ForeColor = Ink;
            }
            return b;
        }

        private void BrowseBtn_Click(object sender, EventArgs e)
        {
            using (FolderBrowserDialog dlg = new FolderBrowserDialog())
            {
                dlg.Description = "Choose where to install Better Keys";
                dlg.SelectedPath = dirTxt.Text;
                if (dlg.ShowDialog(this) == DialogResult.OK)
                    dirTxt.Text = Path.Combine(dlg.SelectedPath, "BetterKeys");
            }
        }

        private void InstallBtn_Click(object sender, EventArgs e)
        {
            string dir = dirTxt.Text.Trim();
            if (dir.Length == 0)
            {
                MessageBox.Show(this, "Please choose an install folder.", "Better Keys Setup",
                    MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }

            installBtn.Enabled = false;
            cancelBtn.Enabled = false;
            dirTxt.Enabled = false;

            string target = dir;
            Thread worker = new Thread(delegate()
            {
                string result = null;
                Exception error = null;
                try
                {
                    result = Program.DoInstall(target, delegate(string s)
                    {
                        try
                        {
                            BeginInvoke(new Action(delegate { statusLbl.Text = s; }));
                        }
                        catch (Exception) { }
                    });
                }
                catch (Exception ex)
                {
                    error = ex;
                }
                BeginInvoke(new Action(delegate
                {
                    if (error != null)
                    {
                        installBtn.Enabled = true;
                        cancelBtn.Enabled = true;
                        dirTxt.Enabled = true;
                        statusLbl.Text = "";
                        MessageBox.Show(this,
                            "Couldn't install Better Keys.\nPlease check the folder and try again.",
                            "Better Keys Setup", MessageBoxButtons.OK, MessageBoxIcon.Error);
                        return;
                    }
                    installedExe = result;
                    installPanel.Visible = false;
                    donePanel.Visible = true;
                }));
            });
            worker.IsBackground = true;
            worker.Start();
        }

        private void FinishBtn_Click(object sender, EventArgs e)
        {
            if (finishChk.Checked && installedExe != null)
            {
                try
                {
                    System.Diagnostics.Process.Start(installedExe);
                }
                catch (Exception) { }
            }
            Close();
        }
    }
}
