package io.quarkiverse.jdbc.edb.it;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;

@Path("/product")
@ApplicationScoped
@Produces(MediaType.TEXT_PLAIN)
public class ProductResource {

    @Inject
    EntityManager entityManager;

    @Inject
    SessionFactory sessionFactory;

    @POST
    @Transactional
    public String create(@QueryParam("name") String name) {
        Product product = new Product(name);
        entityManager.persist(product);
        entityManager.flush();
        return String.valueOf(product.id);
    }

    @GET
    @Path("/{id}")
    public String get(@PathParam("id") Long id) {
        Product product = entityManager.find(Product.class, id);
        if (product == null) {
            throw new NotFoundException();
        }
        return product.name;
    }

    @GET
    @Path("/dialect")
    public String dialect() {
        // unwrap rather than cast: the injected SessionFactory is a CDI client proxy, which
        // implements SessionFactory but not SessionFactoryImplementor.
        return sessionFactory.unwrap(SessionFactoryImplementor.class).getJdbcServices().getDialect()
                .getClass().getSimpleName();
    }
}
