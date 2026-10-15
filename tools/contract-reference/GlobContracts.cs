using System.Text.Json;
using DotNet.Globbing;

internal static class GlobContracts
{
    public static async Task Write(string outputPath)
    {
        var patterns = new[]
        {
            "**", "*", "**/*.txt", "a/**", "a/**/b", "**/file.*", "*.txt", "?",
            "[ab]", "[!ab]", "[a-z]", "[a-zXYZ]", "[0-9]", "[!a-c]", "[[]", "[]]",
            "[*]]", "[*][]]", "[?]", "[broken", "{a,b}", "", "a*?", "*a*", "**a",
            "a**b", "/foo/**", "**/.*", "a\\**\\b", "file[0-9]?.txt", "İ", "ı", "s", "ſ"
        };
        var subjects = new[]
        {
            "", "a", "b", "A", "ab", "aa", "9", "*", "?", "[", "]", "*]", "{a,b}",
            "a/", "a/b", "a/x/b", "a\\b", "a/file.txt", "file.txt", "/file.txt", "file2x.txt",
            "other.csv", "/foo/", "/foo/bar", ".", "foo.", "a\nb", "İ", "ı", "I", "i", "S", "s", "ſ"
        };
        var results = new List<object>();
        foreach (var insensitive in new[] { false, true })
        {
            foreach (var pattern in patterns)
            {
                foreach (var subject in subjects)
                {
                    bool? matched = null;
                    var failed = false;
                    try
                    {
                        var options = new GlobOptions();
                        options.Evaluation.CaseInsensitive = insensitive;
                        matched = Glob.Parse(pattern, options).IsMatch(subject);
                    }
                    catch (Exception)
                    {
                        failed = true;
                    }
                    results.Add(new { pattern, subject, insensitive, matched, failed });
                }
            }
        }
        var json = JsonSerializer.Serialize(results, new JsonSerializerOptions { WriteIndented = true });
        await File.WriteAllTextAsync(outputPath, json + "\n");
        var versions = new[]
        {
            " \tv1\r\n ", "\u0085v1\u0085", "\u001Cv1\u001C", "\u2003v1\u3000",
            "\uFEFFv1\uFEFF", "", "\u00A0", "\u202Fv1\u202F"
        };
        var trimmed = versions.Select(input => new { input, expected = input.Trim() });
        var versionPath = Path.Combine(Path.GetDirectoryName(outputPath)!, "generated-version-contract.json");
        await File.WriteAllTextAsync(versionPath, JsonSerializer.Serialize(trimmed));
        Console.WriteLine($"Generated {results.Count} glob contracts.");
    }
}
