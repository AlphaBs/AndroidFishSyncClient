using System.Text.Json;

internal static class VersionContracts
{
    public static async Task Write(string outputPath)
    {
        var versions = new[]
        {
            " \tv1\r\n ", "\u0085v1\u0085", "\u001Cv1\u001C", "\u2003v1\u3000",
            "\uFEFFv1\uFEFF", "", "\u00A0", "\u202Fv1\u202F"
        };
        var trimmed = versions.Select(input => new { input, expected = input.Trim() });
        await File.WriteAllTextAsync(outputPath, JsonSerializer.Serialize(trimmed));
        Console.WriteLine($"Generated {versions.Length} version contracts.");
    }
}
