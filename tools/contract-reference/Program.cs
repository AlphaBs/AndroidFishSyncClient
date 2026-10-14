using System.Text.Json;
using System.Text.Json.Nodes;
using FishSyncClient;
using FishSyncClient.Files;
using FishSyncClient.FileComparers;
using FishSyncClient.Syncer;

var cases = JsonNode.Parse(await File.ReadAllTextAsync(args[0]))!.AsArray();
foreach (var item in cases) {
    var test = item!.AsObject();
    var temp = Path.Combine(Path.GetTempPath(), "fishsync-contract-" + Guid.NewGuid());
    var sourceRoot = Path.Combine(temp, "source"); var targetRoot = Path.Combine(temp, "target");
    Directory.CreateDirectory(sourceRoot); Directory.CreateDirectory(targetRoot);
    try {
        var pathOptions = new PathOptions();
        var sources = new List<SyncFile>();
        foreach (var node in test["sources"]!.AsArray()) {
            var path = node!["path"]!.GetValue<string>(); var text = node["text"]!.GetValue<string>();
            var fullPath = Path.Combine(sourceRoot, path); Directory.CreateDirectory(Path.GetDirectoryName(fullPath)!); await File.WriteAllTextAsync(fullPath, text);
            using var input = File.OpenRead(fullPath);
            var checksum = node["checksum"]!.GetValue<bool>() ? new SyncFileChecksum("md5", ChecksumAlgorithms.ComputeMD5(input)) : (SyncFileChecksum?)null;
            sources.Add(new LocalSyncFile(RootedPath.Create(sourceRoot, path, pathOptions)) { Metadata = new SyncFileMetadata { Size = new FileInfo(fullPath).Length, Checksum = checksum } });
        }
        foreach (var node in test["targets"]!.AsArray()) { var path = Path.Combine(targetRoot, node!["path"]!.GetValue<string>()); Directory.CreateDirectory(Path.GetDirectoryName(path)!); await File.WriteAllTextAsync(path, node["text"]!.GetValue<string>()); }
        var rules = test["rules"]!.AsArray().Select(node => new SyncRule(Enum.Parse<SyncAction>(node!["action"]!.GetValue<string>()), Enum.Parse<SyncCondition>(node["condition"]!.GetValue<string>()), node["pattern"]!.GetValue<string>(), new LocalFileChecksumComparer())).ToArray();
        var context = test["context"]!.AsObject();
        var result = await new LocalSyncer(targetRoot, pathOptions, new ParallelSyncFilePairSyncer(1)).CompareFiles(sources, new SyncerOptions { Rules = rules, Context = new SyncContext { IsForced = context["isForced"]?.GetValue<bool>() ?? false, IsNewVersion = context["isNewVersion"]?.GetValue<bool>() ?? false } });
        test["expected"] = JsonSerializer.SerializeToNode(new { added = result.AddedFiles.Select(f => f.Path.SubPath).Order().ToArray(), updated = result.UpdatedFilePairs.Select(f => f.Source.Path.SubPath).Order().ToArray(), deleted = result.DeletedFiles.Select(f => f.Path.SubPath).Order().ToArray(), identical = result.IdenticalFilePairs.Select(f => f.Source.Path.SubPath).Order().ToArray() });
    } finally { Directory.Delete(temp, true); }
}
await File.WriteAllTextAsync(args[1], cases.ToJsonString(new JsonSerializerOptions { WriteIndented = true }) + "\n");
Console.WriteLine($"Generated {cases.Count} reference cases.");
