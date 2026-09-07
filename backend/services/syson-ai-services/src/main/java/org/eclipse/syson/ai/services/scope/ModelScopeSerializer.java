/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.syson.ai.services.scope;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.impl.MembershipCacheAdapter;
import org.eclipse.syson.sysml.metamodel.services.textual.SysMLElementSerializer;
import org.eclipse.syson.sysml.metamodel.services.textual.SysMLSerializingOptions;
import org.eclipse.syson.sysml.metamodel.services.textual.utils.FileNameDeresolver;
import org.eclipse.syson.sysml.metamodel.services.textual.utils.Status;
import org.springframework.stereotype.Service;

/**
 * Renders a {@link ModelScope} as textual SysML v2 for use as prompt context.
 *
 * <p>
 * The output is a sequence of fragments, each preceded by a comment naming the element's qualified
 * name, not a single compilable file. That is deliberate: a scope is an arbitrary slice of a model,
 * so the elements in it generally do not share one enclosing namespace, and pretending otherwise
 * would mean inventing package structure that is not in the model. What the model needs is to see
 * real syntax and real names, and it gets both.
 * </p>
 *
 * <p>
 * Serialization uses the same options as the document exporter, so what a model is shown is what
 * the tool would actually write to a file, and it installs a {@link MembershipCacheAdapter} for the
 * duration exactly as {@code SysMLv2DocumentExporter} does - without it, name resolution during
 * serialization is markedly slower on a model of any size.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class ModelScopeSerializer {

    /** Matches SysMLv2DocumentExporter, so prompt context and exported files agree. */
    private static final SysMLSerializingOptions OPTIONS = new SysMLSerializingOptions.Builder()
            .lineSeparator(System.lineSeparator())
            .nameDeresolver(new FileNameDeresolver())
            .indentation("\t")
            .needEscapeCharacter(true)
            .build();

    /**
     * Renders the scope, stopping when the policy's character budget runs out.
     *
     * @param scope
     *            the elements to render
     * @param policy
     *            supplies the character budget
     * @return the rendered scope, flagged as truncated if anything was left out
     */
    public SerializedModelScope serialize(ModelScope scope, ModelScopePolicy policy) {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(policy);

        var reports = new ArrayList<Status>();
        var builder = new StringBuilder();
        int rendered = 0;
        boolean truncated = scope.elementLimitReached();

        var adapters = this.installCacheAdapters(scope);
        try {
            var serializer = new SysMLElementSerializer(OPTIONS, reports::add);
            for (Element element : scope.elements()) {
                String fragment = this.renderFragment(serializer, element);
                if (fragment.isEmpty()) {
                    continue;
                }
                if (builder.length() + fragment.length() > policy.maxCharacters()) {
                    truncated = true;
                    break;
                }
                builder.append(fragment);
                rendered++;
            }
        } finally {
            adapters.forEach(ResourceAdapter::remove);
        }

        return new SerializedModelScope(builder.toString(), rendered, truncated, this.describe(reports));
    }

    private String renderFragment(SysMLElementSerializer serializer, Element element) {
        String text = serializer.doSwitch(element);
        if (text == null || text.isBlank()) {
            // Not every element has a textual form of its own; those are simply not shown.
            return "";
        }
        var fragment = new StringBuilder();
        fragment.append("// ").append(this.nameOf(element)).append(System.lineSeparator());
        fragment.append(text).append(System.lineSeparator()).append(System.lineSeparator());
        return fragment.toString();
    }

    private String nameOf(Element element) {
        String name = element.getElementId();
        String declaredName = element.getDeclaredName();
        if (declaredName != null && !declaredName.isBlank()) {
            name = declaredName;
        }
        String qualifiedName = element.getQualifiedName();
        if (qualifiedName != null && !qualifiedName.isBlank()) {
            name = qualifiedName;
        }
        return name;
    }

    private List<String> describe(List<Status> reports) {
        return reports.stream()
                .map(Status::toString)
                .toList();
    }

    /**
     * Installs a membership cache on every resource set the scope touches. A scope can span
     * documents, and each has to be covered for the cache to help at all.
     */
    private List<ResourceAdapter> installCacheAdapters(ModelScope scope) {
        var installed = new ArrayList<ResourceAdapter>();
        var covered = new ArrayList<Object>();
        for (Element element : scope.elements()) {
            Resource resource = element.eResource();
            if (resource == null) {
                continue;
            }
            ResourceSet resourceSet = resource.getResourceSet();
            Object target = resource;
            if (resourceSet != null) {
                target = resourceSet;
            }
            if (this.containsSame(covered, target)) {
                continue;
            }
            covered.add(target);
            installed.add(ResourceAdapter.install(resource, resourceSet));
        }
        return installed;
    }

    /**
     * Identity containment: two distinct resource sets are never interchangeable here, so equality
     * is the wrong test.
     */
    private boolean containsSame(List<Object> candidates, Object target) {
        boolean found = false;
        for (Object candidate : candidates) {
            if (candidate == target) {
                found = true;
            }
        }
        return found;
    }

    /**
     * Remembers where a cache adapter was installed so it can be taken back off again.
     */
    private record ResourceAdapter(Resource resource, ResourceSet resourceSet, MembershipCacheAdapter adapter) {

        static ResourceAdapter install(Resource resource, ResourceSet resourceSet) {
            var adapter = new MembershipCacheAdapter();
            if (resourceSet != null) {
                resourceSet.eAdapters().add(adapter);
            } else {
                resource.eAdapters().add(adapter);
            }
            return new ResourceAdapter(resource, resourceSet, adapter);
        }

        void remove() {
            if (this.resourceSet != null) {
                this.resourceSet.eAdapters().remove(this.adapter);
            } else {
                this.resource.eAdapters().remove(this.adapter);
            }
        }
    }
}
