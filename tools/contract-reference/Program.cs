using System.Text.Json;
using System.Text.Json.Nodes;
using FishSyncClient;
using FishSyncClient.Files;
using FishSyncClient.FileComparers;
using FishSyncClient.Syncer;

if (args[0] == "--versions")
{
    await VersionContracts.Write(args[1]);
    return;
}

var input = await File.ReadAllTextAsync(args[0]);
var cases = JsonNode.Parse(input)!.AsArray();

foreach (var item in cases)
{
    var fixture = item!.AsObject();
    var temporaryRoot = Path.Combine(
        Path.GetTempPath(),
        "fishsync-contract-" + Guid.NewGuid());
    var sourceRoot = Path.Combine(temporaryRoot, "source");
    var targetRoot = Path.Combine(temporaryRoot, "target");

    Directory.CreateDirectory(sourceRoot);
    Directory.CreateDirectory(targetRoot);

    try
    {
        await CompareFixture(fixture, sourceRoot, targetRoot);
    }
    finally
    {
        Directory.Delete(temporaryRoot, true);
    }
}

var output = cases.ToJsonString(new JsonSerializerOptions { WriteIndented = true });
await File.WriteAllTextAsync(args[1], output + "\n");
Console.WriteLine($"Generated {cases.Count} reference cases.");

static async Task CompareFixture(JsonObject fixture, string sourceRoot, string targetRoot)
{
    var pathOptions = new PathOptions();
    var sources = await CreateSources(fixture["sources"]!.AsArray(), sourceRoot, pathOptions);

    foreach (var target in fixture["targets"]!.AsArray())
    {
        await WriteFixtureFile(targetRoot, target!);
    }

    var rules = CreateRules(fixture["rules"]!.AsArray());
    var context = fixture["context"]!.AsObject();
    var options = new SyncerOptions
    {
        Rules = rules,
        Context = new SyncContext
        {
            IsForced = context["isForced"]?.GetValue<bool>() ?? false,
            IsNewVersion = context["isNewVersion"]?.GetValue<bool>() ?? false
        }
    };
    var syncer = new LocalSyncer(
        targetRoot,
        pathOptions,
        new ParallelSyncFilePairSyncer(1));

    var result = await syncer.CompareFiles(sources, options);
    fixture["expected"] = CreateExpectedResult(result);
}

static async Task<List<SyncFile>> CreateSources(
    JsonArray entries,
    string root,
    PathOptions pathOptions)
{
    var sources = new List<SyncFile>();

    foreach (var entry in entries)
    {
        var path = entry!["path"]!.GetValue<string>();
        var fullPath = await WriteFixtureFile(root, entry);
        using var input = File.OpenRead(fullPath);
        SyncFileChecksum? checksum = null;

        if (entry["checksum"]!.GetValue<bool>())
        {
            checksum = new SyncFileChecksum("md5", ChecksumAlgorithms.ComputeMD5(input));
        }

        var file = new LocalSyncFile(RootedPath.Create(root, path, pathOptions))
        {
            Metadata = new SyncFileMetadata
            {
                Size = new FileInfo(fullPath).Length,
                Checksum = checksum
            }
        };
        sources.Add(file);
    }

    return sources;
}

static async Task<string> WriteFixtureFile(string root, JsonNode entry)
{
    var path = Path.Combine(root, entry["path"]!.GetValue<string>());
    var text = entry["text"]!.GetValue<string>();

    Directory.CreateDirectory(Path.GetDirectoryName(path)!);
    await File.WriteAllTextAsync(path, text);
    return path;
}

static SyncRule[] CreateRules(JsonArray entries)
{
    var rules = new List<SyncRule>();

    foreach (var entry in entries)
    {
        var action = Enum.Parse<SyncAction>(entry!["action"]!.GetValue<string>());
        var condition = Enum.Parse<SyncCondition>(entry["condition"]!.GetValue<string>());
        var pattern = entry["pattern"]!.GetValue<string>();

        rules.Add(new SyncRule(action, condition, pattern, new LocalFileChecksumComparer()));
    }

    return rules.ToArray();
}

static JsonNode? CreateExpectedResult(SyncFileCollectionComparerResult result)
{
    return JsonSerializer.SerializeToNode(new
    {
        added = result.AddedFiles
            .Select(file => file.Path.SubPath)
            .Order()
            .ToArray(),
        updated = result.UpdatedFilePairs
            .Select(pair => pair.Source.Path.SubPath)
            .Order()
            .ToArray(),
        deleted = result.DeletedFiles
            .Select(file => file.Path.SubPath)
            .Order()
            .ToArray(),
        identical = result.IdenticalFilePairs
            .Select(pair => pair.Source.Path.SubPath)
            .Order()
            .ToArray()
    });
}
