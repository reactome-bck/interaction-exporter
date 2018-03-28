package org.reactome.server.tools.interaction.exporter.util;

import org.reactome.server.graph.domain.model.*;
import org.reactome.server.tools.interaction.exporter.psi.SimpleCrossReference;
import psidev.psi.mi.tab.model.CrossReference;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class IdentifierResolver {

	private static final List<String> validDatabases = Arrays.asList("ChEBI",
			"chembl", "EMBL", "ENSEMBL", "entrezgene/locuslink", "go", "IntAct",
			"pdbe", "psi-mi", "pubmed", "reactome", "refseq", "uniprotkb");
	private static final String REACTOME = "reactome";
	private static final List<ReferenceResolver> IDENTIFIER_RESOLVERS = Arrays.asList(
			new EntrezGeneResolver(),
			new EnsemblResolver()
	);

	public static List<CrossReference> getIdentifiers(PhysicalEntity entity) {
		// (entity{stId})-[:referenceEntity]-(re{databaseName,identifier,otherIdentifier[]})-[:crossReference]-(cr{databaseName, identifier})
		// (entity{stId})-[:crossReference]-(cr{databaseName, identifier})
		final List<CrossReference> identifiers = new LinkedList<>();
		identifiers.add(new SimpleCrossReference(REACTOME, entity.getStId(), null));
		if (entity.getCrossReference() != null)
			entity.getCrossReference().stream()
					.map(reference -> new SimpleCrossReference(reference.getDatabaseName(), reference.getIdentifier(), null))
					.forEach(identifiers::add);
		ReferenceEntity re = null;
		if (entity instanceof EntityWithAccessionedSequence) {
			re = ((EntityWithAccessionedSequence) entity).getReferenceEntity();
		} else if (entity instanceof SimpleEntity)
			re = ((SimpleEntity) entity).getReferenceEntity();
		if (re != null) {
			final String id = re instanceof ReferenceIsoform && ((ReferenceIsoform) re).getVariantIdentifier() != null
					? ((ReferenceIsoform) re).getVariantIdentifier()
					: re.getIdentifier();
			identifiers.add(new SimpleCrossReference(re.getDatabaseName(), id, null));
			if (re.getCrossReference() != null)
				re.getCrossReference().stream()
						.map(reference -> new SimpleCrossReference(reference.getDatabaseName(), reference.getIdentifier(), null))
						.forEach(identifiers::add);
			if (re.getOtherIdentifier() != null) {
				re.getOtherIdentifier().stream()
						.map(s -> IDENTIFIER_RESOLVERS.stream()
								.map(resolver -> resolver.resolve(s))
								.filter(Objects::nonNull)
								.findAny()
								.orElse(null))
						.filter(Objects::nonNull)
						.forEach(identifiers::add);
			}
		}
		return identifiers.stream()
				.filter(reference -> valid(reference.getDatabase()))
				.collect(Collectors.toList());
	}

	private static boolean valid(String database) {
		return validDatabases.contains(database);
	}

	private interface ReferenceResolver {
		CrossReference resolve(String identifier);
	}

	private static class EnsemblResolver implements ReferenceResolver {
		private static final String database = "ENSEMBL";
		private Pattern pattern = Pattern.compile("ENSG\\d+");

		@Override
		public CrossReference resolve(String identifier) {
			final Matcher matcher = pattern.matcher(identifier);
			if (matcher.matches())
				return new SimpleCrossReference(database, identifier, null);
			return null;
		}
	}

	private static class EntrezGeneResolver implements ReferenceResolver {

		private static final String database = "entrezgene/locuslink";
		private final Pattern pattern = Pattern.compile("EntrezGene:(\\d+)");

		@Override
		public CrossReference resolve(String identifier) {
			final Matcher matcher = pattern.matcher(identifier);
			if (matcher.matches())
				return new SimpleCrossReference(database, matcher.group(1), null);
			return null;
		}
	}
}