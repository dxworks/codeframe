from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path
from typing import Any


def extract_codeframe_summary(results_directory: str | Path) -> dict[str, Any]:
    target = Path(results_directory)

    try:
        entries = list(target.iterdir())
    except Exception:
        return _create_summary_payload(
            jsonl_files=[],
            files_total=0,
            languages={},
            type_kinds={},
            metrics=_empty_metrics(),
            run_expected_total=0,
            run_files_analyzed=0,
            run_files_with_errors=0,
            run_duration_seconds=0,
            has_data_quality_issues=True,
        )

    jsonl_files = sorted(
        [entry for entry in entries if entry.is_file() and entry.suffix.lower() == '.jsonl'],
        key=lambda value: value.name,
    )

    files_total = 0
    languages: dict[str, int] = {}
    type_kinds: dict[str, int] = {}
    metrics = _empty_metrics()

    run_expected_total = 0
    run_files_analyzed = 0
    run_files_with_errors = 0
    run_duration_seconds = 0

    invalid_lines = 0
    parse_failures = 0

    sql_metrics = {
        'files': 0,
        'createTables': 0,
        'alterTables': 0,
        'createViews': 0,
        'createIndexes': 0,
        'createProcedures': 0,
        'createFunctions': 0,
        'createTriggers': 0,
        'dropOperations': 0,
    }
    cobol_metrics = {
        'files': 0,
        'sections': 0,
        'paragraphs': 0,
        'dataItems': 0,
        'copyStatements': 0,
        'fileDefinitions': 0,
    }
    markdown_metrics = {
        'files': 0,
        'sections': 0,
        'elements': 0,
    }

    language_metrics: dict[str, dict[str, int]] = {}

    for jsonl_file in jsonl_files:
        file_parsed = _parse_jsonl_file(jsonl_file)

        files_total += file_parsed['filesTotal']
        _merge_counter(languages, file_parsed['languages'])
        _merge_counter(type_kinds, file_parsed['typeKinds'])
        _merge_counter(metrics, file_parsed['metrics'])

        run_expected_total += file_parsed['runExpectedTotal']
        run_files_analyzed += file_parsed['runFilesAnalyzed']
        run_files_with_errors += file_parsed['runFilesWithErrors']
        run_duration_seconds += file_parsed['runDurationSeconds']

        invalid_lines += file_parsed['invalidLines']
        parse_failures += file_parsed['parseFailures']

        _merge_counter(sql_metrics, file_parsed['sqlMetrics'])
        _merge_counter(cobol_metrics, file_parsed['cobolMetrics'])
        _merge_counter(markdown_metrics, file_parsed['markdownMetrics'])
        _merge_nested_language_metrics(language_metrics, file_parsed['languageMetrics'])

    coverage_percent = _percent(run_files_analyzed, run_expected_total)
    has_data_quality_issues = (
        parse_failures > 0
        or invalid_lines > 0
        or files_total == 0
        or run_files_with_errors > 0
        or _is_coverage_incomplete(run_expected_total, run_files_analyzed)
    )

    status = _resolve_status(
        jsonl_count=len(jsonl_files),
        files_total=files_total,
        has_data_quality_issues=has_data_quality_issues,
    )

    return _create_summary_payload(
        jsonl_files=jsonl_files,
        files_total=files_total,
        languages=languages,
        type_kinds=type_kinds,
        metrics=metrics,
        run_expected_total=run_expected_total,
        run_files_analyzed=run_files_analyzed,
        run_files_with_errors=run_files_with_errors,
        run_duration_seconds=run_duration_seconds,
        invalid_lines=invalid_lines,
        parse_failures=parse_failures,
        coverage_percent=coverage_percent,
        status=status,
        sql_metrics=sql_metrics,
        cobol_metrics=cobol_metrics,
        markdown_metrics=markdown_metrics,
        language_metrics=language_metrics,
        has_data_quality_issues=has_data_quality_issues,
    )


def _parse_jsonl_file(file_path: Path) -> dict[str, Any]:
    files_total = 0
    languages: dict[str, int] = {}
    type_kinds: dict[str, int] = {}
    metrics = _empty_metrics()

    run_expected_total = 0
    run_files_analyzed = 0
    run_files_with_errors = 0
    run_duration_seconds = 0

    invalid_lines = 0
    parse_failures = 0

    sql_metrics = {
        'files': 0,
        'createTables': 0,
        'alterTables': 0,
        'createViews': 0,
        'createIndexes': 0,
        'createProcedures': 0,
        'createFunctions': 0,
        'createTriggers': 0,
        'dropOperations': 0,
    }
    cobol_metrics = {
        'files': 0,
        'sections': 0,
        'paragraphs': 0,
        'dataItems': 0,
        'copyStatements': 0,
        'fileDefinitions': 0,
    }
    markdown_metrics = {
        'files': 0,
        'sections': 0,
        'elements': 0,
    }

    language_metrics: dict[str, dict[str, int]] = {}

    try:
        for raw_line in file_path.read_text(encoding='utf-8', errors='replace').splitlines():
            line = raw_line.strip()
            if not line:
                continue

            try:
                record = json.loads(line)
            except Exception:
                invalid_lines += 1
                continue

            if not isinstance(record, dict):
                invalid_lines += 1
                continue

            record_kind = str(record.get('kind') or '')
            if record_kind == 'run':
                run_expected_total += _to_int(record.get('total_files'))
                continue

            if record_kind == 'done':
                run_files_analyzed += _to_int(record.get('files_analyzed'))
                run_files_with_errors += _to_int(record.get('files_with_errors'))
                run_duration_seconds += _to_int(record.get('duration_seconds'))
                continue

            if record_kind == 'error':
                run_files_with_errors += 1
                continue

            if 'language' not in record:
                invalid_lines += 1
                continue

            files_total += 1
            language = str(record.get('language') or 'unknown').strip().lower() or 'unknown'
            languages[language] = languages.get(language, 0) + 1

            language_entry = language_metrics.setdefault(
                language,
                {
                    'files': 0,
                    'types': 0,
                    'methods': 0,
                    'fields': 0,
                    'relationships': 0,
                },
            )
            language_entry['files'] += 1

            file_imports = _safe_list(record.get('imports'))
            metrics['importsTotal'] += len(file_imports)

            top_level_fields = _safe_list(record.get('fields'))
            top_level_methods = _safe_list(record.get('methods'))
            top_level_calls = _safe_list(record.get('methodCalls'))
            metrics['topLevelFieldsTotal'] += len(top_level_fields)
            metrics['topLevelMethodsTotal'] += len(top_level_methods)
            metrics['methodCallEdgesTotal'] += len(top_level_calls)
            metrics['methodCallCountTotal'] += _sum_call_count(top_level_calls)

            language_entry['fields'] += len(top_level_fields)
            language_entry['methods'] += len(top_level_methods)
            language_entry['relationships'] += len(top_level_calls)

            for method in top_level_methods:
                if not isinstance(method, dict):
                    continue
                parameters = _safe_list(method.get('parameters'))
                local_variables = _safe_list(method.get('localVariables'))
                method_calls = _safe_list(method.get('methodCalls'))
                metrics['methodParametersTotal'] += len(parameters)
                metrics['methodLocalVariablesTotal'] += len(local_variables)
                metrics['methodCallEdgesTotal'] += len(method_calls)
                metrics['methodCallCountTotal'] += _sum_call_count(method_calls)
                language_entry['relationships'] += len(method_calls)

            type_scan = _scan_types(
                _safe_list(record.get('types')),
                type_kinds,
                language_entry,
            )
            _merge_counter(metrics, type_scan)

            if language == 'sql':
                sql_metrics['files'] += 1
                sql_metrics['createTables'] += len(_safe_list(record.get('createTables')))
                sql_metrics['alterTables'] += len(_safe_list(record.get('alterTables')))
                sql_metrics['createViews'] += len(_safe_list(record.get('createViews')))
                sql_metrics['createIndexes'] += len(_safe_list(record.get('createIndexes')))
                sql_metrics['createProcedures'] += len(_safe_list(record.get('createProcedures')))
                sql_metrics['createFunctions'] += len(_safe_list(record.get('createFunctions')))
                sql_metrics['createTriggers'] += len(_safe_list(record.get('createTriggers')))
                sql_metrics['dropOperations'] += len(_safe_list(record.get('dropOperations')))

            if language == 'cobol':
                cobol_metrics['files'] += 1
                cobol_metrics['sections'] += len(_safe_list(record.get('sections')))
                cobol_metrics['paragraphs'] += len(_safe_list(record.get('paragraphs')))
                cobol_metrics['dataItems'] += len(_safe_list(record.get('dataItems')))
                cobol_metrics['copyStatements'] += len(_safe_list(record.get('copyStatements')))
                cobol_metrics['fileDefinitions'] += len(_safe_list(record.get('fileDefinitions')))

            if language == 'markdown':
                markdown_metrics['files'] += 1
                sections = _safe_list(record.get('sections'))
                markdown_metrics['sections'] += _count_markdown_sections(sections)
                markdown_metrics['elements'] += _count_markdown_elements(sections)
    except Exception:
        parse_failures += 1

    return {
        'filesTotal': files_total,
        'languages': languages,
        'typeKinds': type_kinds,
        'metrics': metrics,
        'runExpectedTotal': run_expected_total,
        'runFilesAnalyzed': run_files_analyzed,
        'runFilesWithErrors': run_files_with_errors,
        'runDurationSeconds': run_duration_seconds,
        'invalidLines': invalid_lines,
        'parseFailures': parse_failures,
        'sqlMetrics': sql_metrics,
        'cobolMetrics': cobol_metrics,
        'markdownMetrics': markdown_metrics,
        'languageMetrics': language_metrics,
    }


def _scan_types(types: list[Any], type_kinds: dict[str, int], language_entry: dict[str, int]) -> dict[str, int]:
    metrics = _empty_metrics()

    for type_info in types:
        if not isinstance(type_info, dict):
            continue

        kind = str(type_info.get('kind') or 'other').strip().lower() or 'other'
        type_kinds[kind] = type_kinds.get(kind, 0) + 1

        metrics['typesTotal'] += 1
        language_entry['types'] += 1

        if _has_value(type_info.get('extendsType')):
            metrics['extendsTotal'] += 1
            language_entry['relationships'] += 1

        implemented = _safe_list(type_info.get('implementsInterfaces'))
        mixins = _safe_list(type_info.get('mixins'))
        metrics['implementsTotal'] += len(implemented)
        metrics['mixinsTotal'] += len(mixins)
        language_entry['relationships'] += len(implemented) + len(mixins)

        fields = _safe_list(type_info.get('fields'))
        properties = _safe_list(type_info.get('properties'))
        methods = _safe_list(type_info.get('methods'))
        nested_types = _safe_list(type_info.get('types'))

        metrics['typeFieldsTotal'] += len(fields)
        metrics['typePropertiesTotal'] += len(properties)
        metrics['typeMethodsTotal'] += len(methods)

        language_entry['fields'] += len(fields) + len(properties)
        language_entry['methods'] += len(methods)

        for method in methods:
            if not isinstance(method, dict):
                continue
            parameters = _safe_list(method.get('parameters'))
            local_variables = _safe_list(method.get('localVariables'))
            method_calls = _safe_list(method.get('methodCalls'))
            metrics['methodParametersTotal'] += len(parameters)
            metrics['methodLocalVariablesTotal'] += len(local_variables)
            metrics['methodCallEdgesTotal'] += len(method_calls)
            metrics['methodCallCountTotal'] += _sum_call_count(method_calls)
            language_entry['relationships'] += len(method_calls)

        nested_metrics = _scan_types(nested_types, type_kinds, language_entry)
        _merge_counter(metrics, nested_metrics)

    return metrics


def _count_markdown_sections(sections: list[Any]) -> int:
    total = 0
    for section in sections:
        if not isinstance(section, dict):
            continue
        total += 1
        total += _count_markdown_sections(_safe_list(section.get('subsections')))
    return total


def _count_markdown_elements(sections: list[Any]) -> int:
    total = 0
    for section in sections:
        if not isinstance(section, dict):
            continue
        total += len(_safe_list(section.get('elements')))
        total += _count_markdown_elements(_safe_list(section.get('subsections')))
    return total


def _create_summary_payload(
    jsonl_files: list[Path],
    files_total: int,
    languages: dict[str, int],
    type_kinds: dict[str, int],
    metrics: dict[str, int],
    run_expected_total: int,
    run_files_analyzed: int,
    run_files_with_errors: int,
    run_duration_seconds: int,
    status: str = 'failed',
    invalid_lines: int = 0,
    parse_failures: int = 0,
    coverage_percent: str = '0',
    sql_metrics: dict[str, int] | None = None,
    cobol_metrics: dict[str, int] | None = None,
    markdown_metrics: dict[str, int] | None = None,
    language_metrics: dict[str, dict[str, int]] | None = None,
    has_data_quality_issues: bool = True,
) -> dict[str, Any]:
    generated_at = _iso_now()

    sql_metrics = sql_metrics or {
        'files': 0,
        'createTables': 0,
        'alterTables': 0,
        'createViews': 0,
        'createIndexes': 0,
        'createProcedures': 0,
        'createFunctions': 0,
        'createTriggers': 0,
        'dropOperations': 0,
    }
    cobol_metrics = cobol_metrics or {
        'files': 0,
        'sections': 0,
        'paragraphs': 0,
        'dataItems': 0,
        'copyStatements': 0,
        'fileDefinitions': 0,
    }
    markdown_metrics = markdown_metrics or {
        'files': 0,
        'sections': 0,
        'elements': 0,
    }
    language_metrics = language_metrics or {}

    language_rows = _build_language_rows(files_total, language_metrics)
    type_kind_rows = _build_type_kind_rows(type_kinds)

    metadata: dict[str, Any] = {
        'jsonl.files': len(jsonl_files),
        'files.total': files_total,
        'run.total.files': run_expected_total,
        'run.files.analyzed': run_files_analyzed,
        'run.files.with.errors': run_files_with_errors,
        'run.duration.seconds': run_duration_seconds,
        'run.coverage.percent': coverage_percent,
        'languages.count': len(languages),
        'types.total': metrics['typesTotal'],
        'relationships.extends.total': metrics['extendsTotal'],
        'relationships.implements.total': metrics['implementsTotal'],
        'relationships.mixins.total': metrics['mixinsTotal'],
        'imports.total': metrics['importsTotal'],
        'method.calls.edges.total': metrics['methodCallEdgesTotal'],
        'method.calls.total': metrics['methodCallCountTotal'],
        'data.invalid.lines': invalid_lines,
        'data.parse.failures': parse_failures,
        'generated.at': generated_at,
    }

    for language, count in sorted(languages.items()):
        metadata[f'languages.{language}.files'] = count

    for kind, count in sorted(type_kinds.items()):
        metadata[f'types.kind.{kind}'] = count

    markdown_lines: list[str] = [
        '## CodeFrame',
        '',
        f'- JSONL files: {_format_int(len(jsonl_files))}',
        f'- Files analyzed: {_format_int(files_total)}',
        f'- Coverage: {_format_int(run_files_analyzed)}/{_format_int(run_expected_total)} ({coverage_percent}%)',
        f'- Files with errors: {_format_int(run_files_with_errors)}',
        f'- Duration: {_format_int(run_duration_seconds)} s',
        f'- Type declarations: {_format_int(metrics["typesTotal"])}',
        f'- Method call edges: {_format_int(metrics["methodCallEdgesTotal"])}',
        f'- Relationship links (extends/implements/mixins): '
        f"{_format_int(metrics['extendsTotal'] + metrics['implementsTotal'] + metrics['mixinsTotal'])}",
        '',
        '### Structural Metrics by Language',
        '',
        '| Language | Files | Share | Types | Methods | Fields/Props | Relationship Links |',
        '| --- | ---: | ---: | ---: | ---: | ---: | ---: |',
    ]

    for row in language_rows:
        markdown_lines.append(
            f"| {row['language']} | {row['filesFormatted']} | {row['share']} | {row['typesFormatted']} "
            f"| {row['methodsFormatted']} | {row['fieldsFormatted']} | {row['relationshipsFormatted']} |"
        )

    markdown_lines.extend(
        [
            '',
            '### Type Kind Breakdown',
            '',
            '| Kind | Count |',
            '| --- | ---: |',
        ]
    )

    for row in type_kind_rows:
        markdown_lines.append(f"| {row['kind']} | {row['countFormatted']} |")

    if sql_metrics['files'] > 0:
        markdown_lines.extend(
            [
                '',
                '### SQL Structural Footprint',
                '',
                f"- SQL files: {_format_int(sql_metrics['files'])}",
                f"- CREATE TABLE: {_format_int(sql_metrics['createTables'])}",
                f"- ALTER TABLE: {_format_int(sql_metrics['alterTables'])}",
                f"- CREATE VIEW: {_format_int(sql_metrics['createViews'])}",
                f"- CREATE INDEX: {_format_int(sql_metrics['createIndexes'])}",
                f"- Routines (procedures/functions/triggers): "
                f"{_format_int(sql_metrics['createProcedures'] + sql_metrics['createFunctions'] + sql_metrics['createTriggers'])}",
                f"- DROP operations: {_format_int(sql_metrics['dropOperations'])}",
            ]
        )

    if cobol_metrics['files'] > 0:
        markdown_lines.extend(
            [
                '',
                '### COBOL Structural Footprint',
                '',
                f"- COBOL files: {_format_int(cobol_metrics['files'])}",
                f"- Sections: {_format_int(cobol_metrics['sections'])}",
                f"- Paragraphs: {_format_int(cobol_metrics['paragraphs'])}",
                f"- Data items: {_format_int(cobol_metrics['dataItems'])}",
                f"- Copy statements: {_format_int(cobol_metrics['copyStatements'])}",
                f"- File definitions: {_format_int(cobol_metrics['fileDefinitions'])}",
            ]
        )

    if markdown_metrics['files'] > 0:
        markdown_lines.extend(
            [
                '',
                '### Markdown Structural Footprint',
                '',
                f"- Markdown files: {_format_int(markdown_metrics['files'])}",
                f"- Sections: {_format_int(markdown_metrics['sections'])}",
                f"- Block elements: {_format_int(markdown_metrics['elements'])}",
            ]
        )

    template_model = {
        'generatedAt': generated_at,
        'isDataQualityPartial': has_data_quality_issues,
        'metrics': {
            'jsonlFilesFormatted': _format_int(len(jsonl_files)),
            'filesTotalFormatted': _format_int(files_total),
            'runTotalFilesFormatted': _format_int(run_expected_total),
            'runFilesAnalyzedFormatted': _format_int(run_files_analyzed),
            'runFilesWithErrorsFormatted': _format_int(run_files_with_errors),
            'runCoveragePercent': coverage_percent,
            'runDurationSecondsFormatted': _format_int(run_duration_seconds),
            'typesTotalFormatted': _format_int(metrics['typesTotal']),
            'extendsTotalFormatted': _format_int(metrics['extendsTotal']),
            'implementsTotalFormatted': _format_int(metrics['implementsTotal']),
            'mixinsTotalFormatted': _format_int(metrics['mixinsTotal']),
            'importsTotalFormatted': _format_int(metrics['importsTotal']),
            'methodCallEdgesTotalFormatted': _format_int(metrics['methodCallEdgesTotal']),
            'methodCallCountTotalFormatted': _format_int(metrics['methodCallCountTotal']),
            'invalidLinesFormatted': _format_int(invalid_lines),
            'parseFailuresFormatted': _format_int(parse_failures),
        },
        'languageRows': language_rows,
        'typeKindRows': type_kind_rows,
        'hasSqlMetrics': sql_metrics['files'] > 0,
        'hasCobolMetrics': cobol_metrics['files'] > 0,
        'hasMarkdownMetrics': markdown_metrics['files'] > 0,
        'sqlMetrics': {
            'filesFormatted': _format_int(sql_metrics['files']),
            'createTablesFormatted': _format_int(sql_metrics['createTables']),
            'alterTablesFormatted': _format_int(sql_metrics['alterTables']),
            'createViewsFormatted': _format_int(sql_metrics['createViews']),
            'createIndexesFormatted': _format_int(sql_metrics['createIndexes']),
            'createProceduresFormatted': _format_int(sql_metrics['createProcedures']),
            'createFunctionsFormatted': _format_int(sql_metrics['createFunctions']),
            'createTriggersFormatted': _format_int(sql_metrics['createTriggers']),
            'dropOperationsFormatted': _format_int(sql_metrics['dropOperations']),
        },
        'cobolMetrics': {
            'filesFormatted': _format_int(cobol_metrics['files']),
            'sectionsFormatted': _format_int(cobol_metrics['sections']),
            'paragraphsFormatted': _format_int(cobol_metrics['paragraphs']),
            'dataItemsFormatted': _format_int(cobol_metrics['dataItems']),
            'copyStatementsFormatted': _format_int(cobol_metrics['copyStatements']),
            'fileDefinitionsFormatted': _format_int(cobol_metrics['fileDefinitions']),
        },
        'markdownMetrics': {
            'filesFormatted': _format_int(markdown_metrics['files']),
            'sectionsFormatted': _format_int(markdown_metrics['sections']),
            'elementsFormatted': _format_int(markdown_metrics['elements']),
        },
    }

    return {
        'tool': 'codeframe',
        'status': status,
        'metadata': metadata,
        'markdown': '\n'.join(markdown_lines),
        'templateModel': template_model,
    }


def _build_language_rows(
    files_total: int,
    language_metrics: dict[str, dict[str, int]],
) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []

    for language, values in language_metrics.items():
        files = values.get('files', 0)
        types = values.get('types', 0)
        methods = values.get('methods', 0)
        fields = values.get('fields', 0)
        relationships = values.get('relationships', 0)

        rows.append(
            {
                'language': language,
                'files': files,
                'types': types,
                'methods': methods,
                'fields': fields,
                'relationships': relationships,
                'filesFormatted': _format_int(files),
                'typesFormatted': _format_int(types),
                'methodsFormatted': _format_int(methods),
                'fieldsFormatted': _format_int(fields),
                'relationshipsFormatted': _format_int(relationships),
                'share': f"{_percent(files, files_total)}%",
            }
        )

    rows.sort(key=lambda row: (-row['files'], -row['types'], row['language']))
    return rows


def _build_type_kind_rows(type_kinds: dict[str, int]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []

    for kind, count in type_kinds.items():
        rows.append(
            {
                'kind': kind,
                'count': count,
                'countFormatted': _format_int(count),
            }
        )

    rows.sort(key=lambda row: (-row['count'], row['kind']))
    return rows


def _resolve_status(jsonl_count: int, files_total: int, has_data_quality_issues: bool) -> str:
    if jsonl_count == 0 or files_total == 0:
        return 'failed'
    if has_data_quality_issues:
        return 'partial'
    return 'success'


def _empty_metrics() -> dict[str, int]:
    return {
        'typesTotal': 0,
        'typeMethodsTotal': 0,
        'typeFieldsTotal': 0,
        'typePropertiesTotal': 0,
        'topLevelMethodsTotal': 0,
        'topLevelFieldsTotal': 0,
        'extendsTotal': 0,
        'implementsTotal': 0,
        'mixinsTotal': 0,
        'importsTotal': 0,
        'methodCallEdgesTotal': 0,
        'methodCallCountTotal': 0,
        'methodParametersTotal': 0,
        'methodLocalVariablesTotal': 0,
    }


def _safe_list(value: Any) -> list[Any]:
    if isinstance(value, list):
        return value
    return []


def _sum_call_count(calls: list[Any]) -> int:
    total = 0
    for call in calls:
        if not isinstance(call, dict):
            total += 1
            continue
        count = call.get('callCount')
        if isinstance(count, int):
            total += max(count, 0)
        else:
            total += 1
    return total


def _merge_counter(target: dict[str, int], source: dict[str, int]) -> None:
    for key, value in source.items():
        target[key] = target.get(key, 0) + value


def _merge_nested_language_metrics(
    target: dict[str, dict[str, int]],
    source: dict[str, dict[str, int]],
) -> None:
    for language, metrics in source.items():
        if language not in target:
            target[language] = {
                'files': 0,
                'types': 0,
                'methods': 0,
                'fields': 0,
                'relationships': 0,
            }
        for metric, value in metrics.items():
            target[language][metric] = target[language].get(metric, 0) + value


def _to_int(value: Any) -> int:
    try:
        return int(value)
    except Exception:
        return 0


def _has_value(value: Any) -> bool:
    if value is None:
        return False
    if isinstance(value, str):
        return value.strip() != ''
    return True


def _is_coverage_incomplete(expected: int, analyzed: int) -> bool:
    if expected <= 0:
        return False
    return analyzed < expected


def _percent(value: int, total: int) -> str:
    if total <= 0:
        return '0'
    percent = (value / total) * 100
    if percent.is_integer():
        return str(int(percent))
    return f'{percent:.2f}'.rstrip('0').rstrip('.')


def _format_int(value: int) -> str:
    return f'{value:,}'


def _iso_now() -> str:
    local_now = datetime.now().astimezone()
    return f"{local_now.strftime('%Y-%m-%d %H:%M:%S')} {_format_gmt_offset(local_now.strftime('%z'))}"


def _format_gmt_offset(offset: str) -> str:
    if len(offset) != 5:
        return 'GMT+0'

    sign = offset[0]
    hours = int(offset[1:3])
    minutes = int(offset[3:5])

    if minutes == 0:
        return f'GMT{sign}{hours}'

    return f'GMT{sign}{hours}:{minutes:02d}'
