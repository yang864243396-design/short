package main

import (
	"flag"
	"fmt"
	"io"
	"os"
	"strings"
)

func printUsage(out io.Writer) {
	fmt.Fprintln(out, "用法: drama-import [选项]")
	fmt.Fprintln(out, "")
	fmt.Fprintln(out, "说明见 README.md 与同目录 DRAMA_BATCH_IMPORT_SPEC.md（状态文件 daoru.txt 为 JSON Lines）。")
	fmt.Fprintln(out, "须在 Linux 上运行（flock）；可在 Windows/macOS 交叉编译：GOOS=linux GOARCH=amd64 go build -o drama-import .")
	fmt.Fprintln(out, "")
}

// cliFlags 绑定到 FlagSet，用于正常解析与 -help 展示。
type cliFlags struct {
	maxDirs        *string
	beforeDate     *string
	sourceRoot     *string
	stagingRoot    *string
	stateFile      *string
	adminURL       *string
	adminUser      *string
	adminPass      *string
	adminPassFile  *string
	allowFuture    *bool
}

func registerFlags(fs *flag.FlagSet) *cliFlags {
	v := &cliFlags{}
	v.maxDirs = fs.String("max-dirs", "", "max directories to advance (optional). Env: IMPORT_MAX_DIRS_PER_RUN")
	v.beforeDate = fs.String("before-date", "", "only ModTime < this date (YYYY-MM-DD local). Env: IMPORT_MOD_TIME_BEFORE")
	v.sourceRoot = fs.String("source-root", "", "default /mnt/DuanJu or IMPORT_SOURCE_ROOT")
	v.stagingRoot = fs.String("staging-root", "", "staging root or IMPORT_STAGING_ROOT")
	v.stateFile = fs.String("state-file", "", "daoru.txt path or IMPORT_STATE_FILE")
	v.adminURL = fs.String("admin-base-url", "", "e.g. http://127.0.0.1:8080/api/v1/admin or IMPORT_ADMIN_BASE_URL")
	v.adminUser = fs.String("admin-user", "", "IMPORT_ADMIN_USER")
	v.adminPass = fs.String("admin-password", "", "IMPORT_ADMIN_PASSWORD (overrides -admin-password-file)")
	v.adminPassFile = fs.String("admin-password-file", "", "file with password, trimmed. Env: IMPORT_ADMIN_PASSWORD_FILE")
	v.allowFuture = fs.Bool("allow-future-before-date", false, "skip mod_time_before <= now+24h validation")
	return v
}

func readAdminPasswordFile(path string) (string, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return "", fmt.Errorf("read admin password file: %w", err)
	}
	s := strings.TrimSpace(string(b))
	if s == "" {
		return "", fmt.Errorf("admin password file %s is empty after trim", path)
	}
	return s, nil
}

// resolveAdminPassword: CLI plain > CLI file > env plain > env file (matches SPEC §2.3 when no YAML layer).
func resolveAdminPassword(cliPass, cliPassFile string) (string, error) {
	if p := strings.TrimSpace(cliPass); p != "" {
		return p, nil
	}
	if f := strings.TrimSpace(cliPassFile); f != "" {
		return readAdminPasswordFile(f)
	}
	if p := strings.TrimSpace(os.Getenv("IMPORT_ADMIN_PASSWORD")); p != "" {
		return p, nil
	}
	if f := strings.TrimSpace(os.Getenv("IMPORT_ADMIN_PASSWORD_FILE")); f != "" {
		return readAdminPasswordFile(f)
	}
	return "", fmt.Errorf("admin password required: use --admin-password, --admin-password-file, IMPORT_ADMIN_PASSWORD, or IMPORT_ADMIN_PASSWORD_FILE")
}

func main() {
	for _, a := range os.Args[1:] {
		if a == "-h" || a == "-help" || a == "--help" {
			fs := flag.NewFlagSet("drama-import", flag.ExitOnError)
			registerFlags(fs)
			printUsage(os.Stdout)
			fs.PrintDefaults()
			return
		}
	}

	cfg := LoadConfigFromEnv()

	fs := flag.NewFlagSet("drama-import", flag.ExitOnError)
	fs.Usage = func() {
		printUsage(os.Stderr)
		fs.PrintDefaults()
	}
	v := registerFlags(fs)

	if err := fs.Parse(os.Args[1:]); err != nil {
		os.Exit(2)
	}

	if *v.sourceRoot != "" {
		cfg.SourceRoot = *v.sourceRoot
	}
	if *v.stagingRoot != "" {
		cfg.StagingRoot = *v.stagingRoot
	}
	if *v.stateFile != "" {
		cfg.StateFile = *v.stateFile
	}
	if *v.adminURL != "" {
		cfg.AdminBaseURL = strings.TrimSuffix(strings.TrimSpace(*v.adminURL), "/")
	}
	if *v.adminUser != "" {
		cfg.AdminUser = *v.adminUser
	}
	cfg.AllowFutureDate = *v.allowFuture

	maxStr := strings.TrimSpace(*v.maxDirs)
	if maxStr == "" {
		maxStr = strings.TrimSpace(os.Getenv("IMPORT_MAX_DIRS_PER_RUN"))
	}
	md, err := ParseOptionalMaxDirs(maxStr)
	if err != nil {
		fmt.Fprintf(os.Stderr, "%v\n", err)
		os.Exit(2)
	}
	cfg.MaxDirsPerRun = md

	beforeStr := strings.TrimSpace(*v.beforeDate)
	if beforeStr == "" {
		beforeStr = strings.TrimSpace(os.Getenv("IMPORT_MOD_TIME_BEFORE"))
	}
	mt, err := ParseModTimeBefore(beforeStr)
	if err != nil {
		fmt.Fprintf(os.Stderr, "%v\n", err)
		os.Exit(2)
	}
	cfg.ModTimeBefore = mt
	if cfg.ModTimeBefore != nil {
		if err := ValidateModTimeUpperBound(*cfg.ModTimeBefore, cfg.AllowFutureDate); err != nil {
			fmt.Fprintf(os.Stderr, "%v\n", err)
			os.Exit(2)
		}
	}

	if strings.TrimSpace(cfg.AdminUser) == "" {
		fmt.Fprintf(os.Stderr, "admin user required: --admin-user or IMPORT_ADMIN_USER\n")
		os.Exit(2)
	}
	pwd, err := resolveAdminPassword(*v.adminPass, *v.adminPassFile)
	if err != nil {
		fmt.Fprintf(os.Stderr, "%v\n", err)
		os.Exit(2)
	}
	cfg.AdminPassword = pwd

	cfg.PrintBanner()

	client := NewClient(cfg.AdminBaseURL, cfg.AdminUser, cfg.AdminPassword)
	if err := client.Login(); err != nil {
		fmt.Fprintf(os.Stderr, "login: %v\n", err)
		os.Exit(1)
	}

	if err := Run(cfg, client); err != nil {
		fmt.Fprintf(os.Stderr, "run: %v\n", err)
		os.Exit(1)
	}
}
